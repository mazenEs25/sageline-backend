# Feature Specification: ValidationMeasure Refactor (Backend)

**Feature Branch**: `002-validation-measure-refactor`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "Phase 002 — ValidationMeasure Refactor (only backend side) from plan.md file"

## Clarifications

### Session 2026-05-11

- Q: Batch create — should the endpoint commit valid entries when some entries are invalid, or reject the whole batch? → A: All-or-nothing (transactional): any invalid entry rejects the entire batch; nothing is persisted; the response itemizes which entries failed and why.
- Q: Which ticket statuses allow measure create / update / delete? → A: `EN_COURS` only — measures are read-only in every other status, including prep stages, `EN_ATTENTE_HANDOVER`, `EN_REVUE`, and all terminal statuses.
- Q: Which roles may create / update / delete measures? → A: `TECH_VAL`, `TECH_PREP`, and `ADMIN_IT`. Read/list is open to any authenticated user who can already see the ticket.
- Q: How should concurrent updates to the same measure be resolved? → A: Last-writer-wins. The second update overwrites the first; `enteredBy` and `measuredAt` are refreshed on every write; no caller-side version token is required. Audit relies on those two stamped fields.
- Q: How should the one-time legacy migration map `expectedValue` to a bounded tolerance window? → A: Symmetric ±5% spread of `expectedValue`. When `expectedValue == 0`, use a fixed absolute spread of ±0.5 (in whatever unit the legacy row carried) to avoid a zero-width window. Status is forced to match the legacy `conform` boolean (`true → OK`, `false → OUT_OF_RANGE`) regardless of the resulting deviation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record an industrial measure with bounded tolerance (Priority: P1)

A validation technician (TECH_VAL) working on an open ticket records a measured value for a single industrial measure (e.g. Wi-Fi power on antenna 1 at 5500 MHz). They submit the measured numeric value; the system must persist the measure tied to the ticket, automatically classify it as `OK` / `OUT_OF_RANGE` / `NOT_EXECUTED` against its lower/upper bounds, compute a deviation percentage relative to the tolerance window, and stamp the operator and timestamp on the record.

**Why this priority**: This is the foundational capability the whole refactor exists to deliver. Without bounded-tolerance, status-aware, per-ticket measures, no downstream phase (workflow guard, log importer, conformity engine, KPIs by measure) can function. It also replaces the legacy `expectedValue/conform` schema that no longer matches the industrial reality.

**Independent Test**: With a seeded ticket and a known reference measure template (`lowerBound=13.5`, `upperBound=16.5`, unit `dBm`), POST a measured value of `15.5` and assert the persisted record has `status=OK`, `deviationPct ≈ 33.3`, operator identity recorded, and the measure is returned by the ticket's measures listing.

**Acceptance Scenarios**:

1. **Given** an active ticket on a zone whose poste type has a catalog template `POWER_RMS_AVG_VSA1` with bounds `[13.5, 16.5] dBm`, **When** the technician records a measured value of `15.5 dBm` against that template, **Then** the system persists a measure with `status=OK`, `deviationPct ≈ 33.3`, the operator's identity, and the current timestamp.
2. **Given** the same template, **When** the technician records a measured value of `20.0 dBm`, **Then** the persisted measure has `status=OUT_OF_RANGE` and `deviationPct ≈ 333`.
3. **Given** the same template, **When** the technician creates a measure with no measured value provided, **Then** the persisted measure has `status=NOT_EXECUTED` and `deviationPct` is null/undefined.
4. **Given** an existing measure on the ticket, **When** the technician updates its measured value, **Then** `status` and `deviationPct` are recomputed automatically on save without requiring a manual recompute call.

---

### User Story 2 - Seed a new ticket with the full catalog of expected measures (Priority: P1)

When a ticket is created (or when a technician explicitly asks to populate it), the system must be able to instantiate, in one operation, every catalog measure expected for the ticket's zone poste type as a `NOT_EXECUTED` placeholder. This gives the technician a ready-to-fill checklist instead of an empty panel and lets the rest of the system reason about "filled vs. unfilled" coverage.

**Why this priority**: Bounded measures only deliver value when the operator can see the *full expected set* up front. Without bulk-seeding, every ticket would start empty and the future workflow guard (Phase 003) would have nothing to compare against. This story is also a precondition for the log importer (Phase 004) which will fill in pre-seeded slots.

**Independent Test**: For a ticket on a zone whose poste type catalog has 16 mandatory measures, invoking the "instantiate-from-catalog" operation must result in 16 measures persisted against the ticket, all with `status=NOT_EXECUTED`, codes, labels, bounds, units, and context fields (antenna, frequency, modulation scheme when present) copied from the catalog.

**Acceptance Scenarios**:

1. **Given** a ticket on a `WIFI_CONDUIT` zone with a catalog of 16 templates, **When** the technician triggers "instantiate from catalog", **Then** 16 measures are created on the ticket, each linked to its template, each with `status=NOT_EXECUTED`, and the catalog bounds/unit/context fields are copied onto the measure.
2. **Given** the same ticket already has 16 catalog-linked measures, **When** the technician triggers "instantiate from catalog" again, **Then** the operation is idempotent — no duplicate measures are created and the existing measures are not overwritten.
3. **Given** a ticket on a zone whose poste type catalog is empty, **When** the technician triggers "instantiate from catalog", **Then** the operation succeeds with zero measures created and a clear indication of "catalog empty for this poste type".

---

### User Story 3 - Record multiple measures in one operation (Priority: P2)

A technician (or an automated process such as a future log importer) records a batch of measured values for several measures on the same ticket in a single request. Each entry in the batch is independently validated and classified, and the system reports a per-entry outcome so partial successes are visible.

**Why this priority**: Hand-entering 16+ measures one by one is operationally painful and would be the primary user complaint after this phase ships. It is also the contract the Phase 004 log importer will call to commit parsed measures.

**Independent Test**: POST a batch of 5 measure payloads to a ticket; assert that 5 measures are persisted, each with the correct `status` and `deviationPct`, and the response itemizes the created records. Then submit a batch where one entry is invalid (e.g., references a non-existent template) and assert the response clearly identifies the failing entry.

**Acceptance Scenarios**:

1. **Given** a ticket and 5 valid measure payloads, **When** the technician submits them as a single batch, **Then** all 5 measures are persisted, each with its own computed `status` and `deviationPct`, and the response lists the 5 created records.
2. **Given** a batch where one entry references a non-existent template id, **When** the batch is submitted, **Then** the entire batch is rejected, no measures are persisted, and the response identifies which entry failed and why.

---

### User Story 4 - Record measures outside the catalog (ad-hoc) (Priority: P2)

A technician encounters a measure that is not in the poste type's catalog (a one-off check, a newly introduced test, or an exploratory reading) and needs to record it on the ticket anyway. The system must accept a measure that is not linked to any catalog template, as long as the caller supplies the minimum context: a measure code, a measured value (or explicit null for NOT_EXECUTED), a unit, and bounds. The measure is then classified and persisted like any other.

**Why this priority**: Industrial reality includes exploratory readings and gaps between when a new test is introduced on the line and when the catalog is updated. Refusing ad-hoc measures would force technicians to wait for an admin to extend the catalog before they can record what they just measured.

**Independent Test**: POST a measure payload to a ticket without a template id but with an explicit `measureCode`, `measuredValue`, `unit`, `lowerBound`, `upperBound`; assert it is persisted with `catalogTemplate=null`, the supplied context, and the correctly computed `status` and `deviationPct`.

**Acceptance Scenarios**:

1. **Given** a ticket, **When** the technician records an ad-hoc measure with code `SCRATCH_TEST`, measured `12.0`, bounds `[10.0, 14.0]`, unit `V`, **Then** the measure is persisted unlinked to any template, with `status=OK` and `deviationPct ≈ 50`.
2. **Given** the catalog later adds a template for `SCRATCH_TEST`, **When** existing ad-hoc measures with that code are queried, **Then** they remain unlinked (no retro-association is required by this phase) and continue to display their originally supplied bounds.

---

### User Story 5 - List, update, and remove measures on a ticket (Priority: P2)

A technician needs to view all measures currently attached to a ticket, correct a mis-typed value, or remove a measure that was created in error. All these operations must be available and must keep the per-measure `status` and `deviationPct` consistent with the latest measured value.

**Why this priority**: Without retrieval, the technician cannot see what was already recorded. Without update and delete, every typo would orphan a ticket. These are necessary to make the create operations usable in practice.

**Independent Test**: Create a measure, retrieve the ticket's measure list and confirm it appears, update its measured value and confirm `status`/`deviationPct` are recomputed, delete it and confirm it no longer appears in the list.

**Acceptance Scenarios**:

1. **Given** a ticket with several recorded measures, **When** the user queries the ticket's measures, **Then** the system returns the complete list with all per-measure fields (code, label, value, unit, bounds, status, deviation, antenna/frequency/modulation when set, operator, timestamp, source-log marker when set).
2. **Given** a measure with `measuredValue=15.5` and `status=OK`, **When** its measured value is updated to `20.0`, **Then** the persisted record reflects `status=OUT_OF_RANGE` and the recomputed deviation, without any caller-side recompute.
3. **Given** a measure on a ticket, **When** it is deleted, **Then** it no longer appears in the ticket's measure list and the ticket itself remains intact.

---

### User Story 6 - Existing legacy clients keep working during the transition (Priority: P3)

The frontend and any external scripts that currently call the legacy "validation results" API must continue to receive successful responses during the transition window, with a clear signal that the endpoint is deprecated so callers can migrate. Legacy data already persisted under the old schema must remain visible (i.e., it must be migrated into the new schema as part of this phase's rollout).

**Why this priority**: The constitution mandates backward compatibility during the refactor (at least one phase of overlap before legacy removal). Without it, the frontend breaks the day the backend ships and the refactor cannot land safely.

**Independent Test**: Call the legacy results endpoint against a ticket that previously had legacy results; confirm a successful response and a deprecation signal in the response headers. Confirm that the same ticket, queried through the new measures endpoint, returns the migrated measures with best-effort mapping applied.

**Acceptance Scenarios**:

1. **Given** a ticket that previously had legacy results recorded under the old schema, **When** the new measures endpoint is queried for that ticket, **Then** the migrated measures are returned, with `parameter` mapped to a measure code, the legacy `expectedValue` mapped to a symmetric tolerance window, and the legacy boolean `conform` mapped to `OK` or `OUT_OF_RANGE`.
2. **Given** a caller still uses the legacy results endpoint, **When** that endpoint is invoked, **Then** the call succeeds and the response carries an explicit deprecation indicator (HTTP header) so the caller can detect and migrate.

---

### Edge Cases

- A measured value equal to a bound (exactly `lowerBound` or exactly `upperBound`) is treated as inside the tolerance window (`status=OK`, `deviationPct = 100`).
- A measured value at the center of the window yields `deviationPct = 0`.
- A catalog template with `lowerBound == upperBound` (zero-width tolerance) must not cause a divide-by-zero; the system either rejects such a template upstream (Phase 001 concern) or treats any non-matching value as `OUT_OF_RANGE` with a defined sentinel deviation.
- An attempt to record a measure on a ticket that is in a status that disallows edits (e.g. already closed) is rejected with a clear, ticket-status-aware error.
- An attempt to instantiate-from-catalog on a ticket whose zone's poste type catalog has been emptied succeeds with zero measures and a clear empty-catalog indication, not an opaque failure.
- An update that sets `measuredValue` back to null transitions the measure to `status=NOT_EXECUTED` and clears `deviationPct`.
- Two callers updating the same measure concurrently are resolved by last-writer-wins: the second update overwrites the first, and `enteredBy` plus `measuredAt` are refreshed on every write so the override is auditable. No caller-side version token is required in this phase.
- A batch that mixes valid and invalid entries is rejected in its entirety (no measures persisted) and the response identifies each failing entry and the reason.
- A legacy result whose `parameter` value does not map cleanly to any catalog code remains accessible (it is migrated as an ad-hoc measure with the parameter string used as the measure code) rather than being dropped silently.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized technician to record a measured value against a ticket, optionally linked to a catalog template, with at minimum: measure code, measured value (nullable), unit, lower bound, upper bound.
- **FR-002**: The system MUST automatically classify each persisted measure as `OK`, `OUT_OF_RANGE`, or `NOT_EXECUTED` based on whether the measured value is within `[lowerBound, upperBound]`, outside that window, or absent.
- **FR-003**: The system MUST automatically compute a deviation percentage for every measure that has a measured value, defined as the absolute distance from the center of the tolerance window divided by half the window's width, expressed as a percentage. A value at the center yields 0%; a value at either bound yields 100%; a value outside the window exceeds 100%.
- **FR-004**: The system MUST recompute `status` and `deviationPct` on every create and every update of a measure, with no need for callers to set those fields explicitly.
- **FR-005**: The system MUST associate every persisted measure with the ticket it belongs to, the operator who recorded it, and the timestamp at which it was recorded.
- **FR-006**: The system MUST allow a measure to be linked to a catalog template (carrying the catalog's bounds, unit, label, category, and optional antenna/frequency/modulation context) or to be ad-hoc (no template link, with the caller supplying the same context fields directly).
- **FR-007**: The system MUST expose, for any ticket, a retrieval operation returning every measure currently attached to that ticket with all of its fields.
- **FR-008**: The system MUST allow an authorized technician to update an existing measure's measured value, optional context fields, and bounds (when ad-hoc), and to delete an existing measure.
- **FR-009**: The system MUST support a batch create operation that accepts multiple measure payloads for a single ticket in one request. The batch MUST be transactional (all-or-nothing): if any entry is invalid the entire batch is rejected, no measures are persisted, and the response identifies each failing entry and its rejection reason.
- **FR-010**: The system MUST support an instantiate-from-catalog operation that, given a ticket, creates one measure per catalog template defined for the ticket's zone poste type, each with `status=NOT_EXECUTED` and template fields copied onto the measure. The operation MUST be idempotent: re-invoking it MUST NOT create duplicates of existing catalog-linked measures.
- **FR-011**: The system MUST migrate existing legacy validation results into the new measure schema as part of this phase's deployment, mapping: legacy `parameter` → measure code; legacy `expectedValue` → a symmetric tolerance window of ±5% of `expectedValue` (and, when `expectedValue == 0`, a fixed absolute spread of ±0.5 in the legacy unit to avoid a zero-width window); legacy `conform=true` → `status=OK`; legacy `conform=false` → `status=OUT_OF_RANGE`. The legacy `conform` boolean is the source of truth for the migrated status and overrides whatever the bounded-tolerance rule would compute. The legacy table itself MUST NOT be dropped in this phase.
- **FR-012**: The legacy "validation results" API endpoint MUST continue to return successful responses during this phase, with a deprecation indicator on every response (HTTP response header) signaling callers to migrate.
- **FR-013**: The system MUST persist, on each measure, a placeholder for source-log traceability (`sourceLogFile`) so that future log-import flows (Phase 004) can populate it without further schema changes. When the field is empty, the measure is assumed manually entered.
- **FR-014**: The system MUST optionally persist, on each measure, antenna identifier, frequency in MHz, and modulation scheme, to allow downstream phases (workflow guard, KPIs) to disambiguate measures sharing the same code across different physical contexts.
- **FR-015**: The system MUST reject create, update, and delete operations on measures unless the owning ticket is in status `EN_COURS`. In every other status (prep stages, `EN_ATTENTE_HANDOVER`, `EN_REVUE`, and all terminal statuses) measures MUST be read-only and the system MUST return a status-aware error. The instantiate-from-catalog operation follows the same rule.
- **FR-016**: All measure-management endpoints MUST be gated by the existing role model. Create, update, delete, batch-create, and instantiate-from-catalog MUST be restricted to roles `TECH_VAL`, `TECH_PREP`, and `ADMIN_IT`. Read/list operations MUST be available to any authenticated user who can already see the owning ticket. Other roles (e.g., `CHEF_SECTEUR`, `EXPERT`, `RESPONSABLE`) MUST receive HTTP 403 on any mutating measure operation.
- **FR-017**: The system MUST validate that an ad-hoc measure payload supplies the minimum context (measure code, unit, lower and upper bound) and reject payloads missing it with a clear error.
- **FR-018**: The system MUST treat a measured value exactly equal to either bound as inside the tolerance window (`status=OK`).

### Key Entities *(include if feature involves data)*

- **ValidationMeasure**: A single measured value recorded against a validation ticket. Carries the measure's industrial identity (code, label, category, unit), its tolerance window (lower bound, upper bound), the actual measured value (nullable), the computed status (`OK` / `OUT_OF_RANGE` / `NOT_EXECUTED`) and deviation percentage, optional physical context (antenna, frequency in MHz, modulation scheme), traceability fields (operator who recorded it, timestamp, optional source log file), and an optional link to the catalog template it instantiates. Belongs to exactly one ticket.
- **MeasureStatus** (enum, introduced in Phase 001 and consumed here): `OK`, `OUT_OF_RANGE`, `NOT_EXECUTED`.
- **MeasureCategory** (enum, introduced in Phase 001 and consumed here): the family of physical quantity (power, voltage, current, frequency, etc.).
- **PosteMeasureCatalog** (defined in Phase 001): the reference template a `ValidationMeasure` may be instantiated from. This phase only consumes it for the instantiate-from-catalog operation and for copying defaults onto a new measure.
- **Validation (ticket)** (existing): the owning aggregate. Measures are scoped to a single ticket and inherit the ticket's zone (which determines the relevant catalog poste type).
- **Legacy ValidationResult** (existing, deprecated by this phase): kept readable behind a deprecated endpoint for one phase; its rows are migrated into `ValidationMeasure` at deployment time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For any measure with a numeric measured value, the persisted `status` and `deviationPct` match the bounded-tolerance specification in 100% of integration-test fixtures, including the canonical cases: `measured=15.5` on bounds `[13.5, 16.5]` → `OK`, deviation ≈ 33.3; `measured=20.0` on bounds `[13.5, 16.5]` → `OUT_OF_RANGE`, deviation ≈ 333; `measured=null` → `NOT_EXECUTED`, no deviation.
- **SC-002**: A technician can fully populate a 16-measure `WIFI_CONDUIT` ticket — instantiate from catalog, then record values for all 16 measures — in fewer than three operator-initiated operations against the API (e.g., one instantiate-from-catalog plus one bulk-update, or one batch create) and in under two minutes of clock time against a local environment.
- **SC-003**: After deployment, every legacy validation result that previously existed in the database is reachable through the new measures retrieval operation for its ticket; the count of migrated measures equals the count of pre-migration legacy results.
- **SC-004**: Every call to the legacy "validation results" endpoint during the transition window returns a successful response *and* carries a machine-readable deprecation indicator, with zero exceptions across the integration test suite.
- **SC-005**: Re-invoking the instantiate-from-catalog operation on the same ticket creates zero additional measures (idempotency verified by integration test).
- **SC-006**: The new measure endpoints return within a target response time consistent with the rest of the ticket API on a representative ticket (≤ 300 ms for read, ≤ 500 ms for batch create of up to 20 entries) on a local development environment.
- **SC-007**: No measure can be created, updated, or deleted by a user who lacks the role required for that ticket's lifecycle stage; the integration test suite covers at least one positive and one negative role-gating case per mutating endpoint.

## Assumptions

- The `PosteMeasureCatalog` entity, the `MeasureStatus` enum, and the `MeasureCategory` enum delivered by Phase 001 are available and stable; this phase consumes them as-is without modification.
- The existing `Validation` (ticket) entity and its lifecycle (`TicketStatus` workflow described in the project's `CLAUDE.md`) remain unchanged in this phase. The workflow *guard* that uses measure coverage to gate transitions is the subject of Phase 003, not this phase.
- The role model (`TECH_VAL`, `TECH_PREP`, `CHEF_SECTEUR`, `EXPERT`, `RESPONSABLE`, `ADMIN_IT`) and the existing ticket-edit authorization rules are reused; this phase introduces no new roles.
- The frontend is explicitly out of scope for this specification, per the user's instruction "only backend side". The frontend deliverables described in `Plan.md §7` will be specified separately.
- Legacy migration spread is locked at ±5% of `expectedValue` (with a fixed ±0.5 absolute spread when `expectedValue == 0`). The migrated `status` is forced to match the legacy `conform` boolean and does not re-derive from the bounded-tolerance rule. See FR-011.
- The legacy `validation_results` table is *not* dropped in this phase. Its physical removal is deferred until Phase 005 closes, per the project constitution's "backward compatibility during refactor" rule.
- Editable-status policy is locked: measures are mutable only while the ticket is in `EN_COURS`; all other statuses (prep stages, `EN_ATTENTE_HANDOVER`, `EN_REVUE`, terminal) are read-only for measures. This is reflected in FR-015 and in the corresponding edge case.
- The Phase 003 workflow guard, Phase 004 log importer, and downstream phases will *consume* the schema defined here without requiring further schema changes; the `sourceLogFile` and antenna/frequency/modulation context fields are introduced now precisely to avoid re-migration later.
- The system already provides operator-identity resolution from the authenticated request (existing `SecurityUtils`-based auditing), so the "stamp the operator on each measure" requirement reuses that infrastructure rather than re-implementing it.
