# Feature Specification: PosteType Catalog (Backend Only)

**Feature Branch**: `001-poste-type-catalog`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "Phase 001 PosteType Catalog — backend only; frontend is handled separately in another project."

> **Scope note (explicit user constraint).** This phase is **backend-only**. The
> `Plan.md` "Frontend deliverables" sub-section of Phase 001 is **OUT OF SCOPE** for
> this spec. The catalog is exposed as a REST contract; the consuming Angular project
> is developed and shipped independently and will integrate against the published
> contract.

## Clarifications

### Session 2026-05-11

- Q: Schema migration mechanism for the catalog (and the rest of the refactor)? → A: Introduce **Flyway**; switch Hibernate to `ddl-auto=validate`; SQL migration files `V1.1__poste_catalog.sql` (DDL) and `V1.2__seed_poste_catalog.sql` (seed) live under `src/main/resources/db/migration/`.
- Q: How should later catalog edits to the `mandatory` flag affect in-flight tickets? → A: **Snapshot at ticket creation.** The `mandatory` flag remains mutable on the catalog template, but every ticket carries its own copy of each measure's `mandatory` value as it was at ticket creation. Later catalog edits affect only newly opened tickets; the workflow guard (Phase 003) reads the snapshot, not the live catalog. The catalog API MUST therefore expose the current `mandatory` value on every read so consumers can snapshot it.
- Q: How should the seed handle measures whose supervisor logs show a measured value but no explicit `[min, max]` bounds? → A: **Seed inactive.** The row is inserted with placeholder bounds derived from the observed value, but `active=false` so the template is hidden from default reads and from workflow/conformity computation. A curator (`ADMIN_IT` or `CHEF_SECTEUR`) MUST explicitly enter engineering-validated bounds and flip `active=true` before the template participates in any ticket. Every such row in the seed migration MUST carry an inline SQL comment naming the source log file and the placeholder rule used.
- Q: Should catalog templates carry audit fields? → A: **Full audit quartet** — `createdAt`, `createdBy`, `updatedAt`, `updatedBy` populated automatically by Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`) bound to `SecurityUtils.getCurrentUserId()`. Soft-delete events are captured implicitly via `updatedBy`/`updatedAt` since deactivation is an update. The four fields MUST be surfaced on read response DTOs.

## User Scenarios & Testing *(mandatory)*

The "users" of this catalog are (a) human operators acting through the existing
SageLine admin API surface, and (b) other backend components (the workflow guard, the
log importer, the KPI service) that will be built in later phases. All scenarios are
exercised through the HTTP API and through automated tests — no UI is in scope.

### User Story 1 — Seeded Catalog Available for Three Real Postes (Priority: P1)

When the application starts on a fresh database, the catalog is automatically populated
with the reference measure templates for the three production postes that the
supervisor provided log files for: `TEST_FONCTIONNEL` (BNFT decoder bench),
`WIFI_CONDUIT` (BWC WiFi gateway bench), and `ACC` (BTF voice-gateway bench). Any
authenticated backend consumer (workflow guard, log importer, KPI service, or a human
calling the API) can immediately list and read these templates.

**Why this priority**: Nothing else in the refactor can be built without these
templates. The workflow guard (Phase 003) needs them to know what is mandatory; the
log importer (Phase 004) needs them to map parsed measure codes; the KPI service
(Phase 006) needs them for per-poste aggregations. Without seed data, the system is a
shell.

**Independent Test**: After a clean startup, calling the public read endpoints returns
≥6 templates for `TEST_FONCTIONNEL`, ≥16 templates for `WIFI_CONDUIT`, and ≥14
templates for `ACC`, each carrying a `measureCode`, a `measureLabel`, a `category`, a
`defaultUnit`, a `defaultLowerBound`, a `defaultUpperBound`, and a `mandatory` flag.
The seed runs idempotently — restarting the application does not duplicate entries.

**Acceptance Scenarios**:

1. **Given** a fresh database, **When** the application has started and an
   authenticated consumer requests the full catalog for `WIFI_CONDUIT`, **Then** the
   response contains at least 16 measure templates whose codes match the
   `POWER_RMS_AVG_VSA1` family observed in the BWC supervisor log (across four
   antennas and four frequencies), each with non-null bounds and unit `dBm`.
2. **Given** the application has already seeded the catalog on a previous start,
   **When** the application restarts, **Then** the catalog row count for the three
   seeded postes is unchanged (no duplication).
3. **Given** a seeded catalog, **When** an authenticated consumer requests the catalog
   for `TEST_FONCTIONNEL`, **Then** the response contains the six power and timing
   measures (`PWR0_2G`, `PWR1_2G`, `PWR0_5G`, `PWR1_5G`, `PWR0_BT`, `Temps_Test`) with
   appropriate units and bounds.

---

### User Story 2 — Catalog Curation by Authorized Operators (Priority: P1)

An `ADMIN_IT` or `CHEF_SECTEUR` operator can add a new measure template to a poste,
edit its bounds or its mandatory flag, and soft-delete (deactivate) an obsolete
template, all through the REST API. Users without those roles can read the catalog
but cannot mutate it.

**Why this priority**: Reality drifts — new postes appear on the line, tolerances are
revised after engineering reviews, obsolete measures get retired. The catalog must be
curatable through the API by authorized personnel without requiring a code change and
redeploy. Read access stays open to all authenticated roles because every downstream
service needs it.

**Independent Test**: With an `ADMIN_IT` token, a new measure template can be created
for any `PosteType`, then updated (bounds widened), then soft-deleted; the same
sequence with an `EXPERT` token receives HTTP 403 on each mutating call. The
soft-deleted template stops appearing in default reads but is still retrievable when
the `includeInactive=true` query parameter is set.

**Acceptance Scenarios**:

1. **Given** an authenticated `ADMIN_IT` user, **When** they create a new measure
   template with a `(posteType, measureCode)` pair that does not yet exist, **Then**
   the system returns HTTP 201 with the created template's identifier and the
   template is immediately visible on subsequent reads.
2. **Given** an existing measure template, **When** an `ADMIN_IT` updates the lower
   and upper bounds, **Then** the persisted template reflects the new bounds and the
   `displayOrder` is preserved unless explicitly changed.
3. **Given** an existing active measure template, **When** an `ADMIN_IT` soft-deletes
   it, **Then** default reads no longer include it but reads with
   `includeInactive=true` do, and downstream services can still resolve historical
   references to it.
4. **Given** an authenticated `EXPERT` (read-only on this catalog) user, **When** they
   attempt to create or update a template, **Then** the system returns HTTP 403 and
   no persistence side effect occurs.
5. **Given** an existing template, **When** any authenticated user attempts to create
   a second template with the same `(posteType, measureCode)` pair, **Then** the
   system returns HTTP 409 (conflict) and does not persist a duplicate.

---

### User Story 3 — Read-Only Catalog Access for Downstream Backend Services (Priority: P2)

Other backend components (workflow guard, log importer, KPI service) need to enumerate
the catalog for a given `PosteType` and retrieve a specific template by its identifier
or by its `(posteType, measureCode)` pair. The endpoints answer in well under a
second for any single poste.

**Why this priority**: This is the contract every later phase consumes. It must be
shaped now so the downstream phases can be designed against it. It is P2 (not P1)
because seed data and curation (P1) are what unblock the dependent phases; this story
ensures the read shape is fit for purpose.

**Independent Test**: For each of the three seeded postes, requesting the catalog
returns the expected list within 200 ms on a development laptop with a database of
≤1 000 templates. Requesting a specific template by id returns it; requesting a
non-existent template returns 404; requesting the catalog for an unrecognized poste
returns an empty list (not an error).

**Acceptance Scenarios**:

1. **Given** a seeded catalog, **When** a consumer requests "measures only" for
   `ACC`, **Then** the response is a stream of measure templates without parent
   poste metadata, suitable for direct consumption by the workflow-guard rule.
2. **Given** a seeded catalog, **When** a consumer requests a template by its unique
   identifier, **Then** the full template (including `displayOrder`, `antenna`,
   `frequencyMhz`, `modulationScheme` if present) is returned.
3. **Given** an empty catalog for a particular `PosteType`, **When** a consumer asks
   for that poste's measures, **Then** the response is HTTP 200 with an empty array
   (not 404).

---

### User Story 4 — Bulk Authoring of Templates for a Newly Introduced Poste (Priority: P3)

An `ADMIN_IT` operator can submit a batch of measure templates for one `PosteType` in
a single request. The system validates the batch as a whole: if any single template
violates a rule (duplicate code within the batch, conflict with an existing template,
missing required field), the entire batch is rejected and no row is persisted.

**Why this priority**: Seeding a brand-new poste with 10+ measures one call at a time
is tedious and error-prone. A batch endpoint is a quality-of-life feature that
mirrors the "Bulk-import" capability the frontend project will eventually use. It is
P3 because the per-template endpoints (US2) are sufficient for the MVP — batch is an
enhancement.

**Independent Test**: An `ADMIN_IT` submits a batch of 12 templates for a new
`PosteType`; if all valid, all 12 are created atomically. Resubmitting the same batch
yields HTTP 409 and no duplicates. Submitting a batch where one entry has a
duplicate `measureCode` (within the batch or against the database) yields HTTP 422
with a per-entry error list and zero rows persisted.

**Acceptance Scenarios**:

1. **Given** an `ADMIN_IT`, **When** they submit a batch of N valid templates for a
   `PosteType`, **Then** the system returns HTTP 201 with all N identifiers and the
   templates are visible on subsequent reads.
2. **Given** a batch with one invalid entry, **When** an `ADMIN_IT` submits it,
   **Then** the system returns HTTP 422 with the index and reason for each invalid
   entry, and the database state is unchanged (atomic rollback).

---

### Edge Cases

- **Duplicate measure code within the same poste.** The system rejects the second
  insert with HTTP 409 and a body that identifies which existing template the request
  conflicts with.
- **Same measure code across different postes.** Allowed: `POWER_RMS_AVG_VSA1` may
  exist independently in `WIFI_CONDUIT` and in a future `WIFI_RY` catalog. Uniqueness
  is on the pair `(posteType, measureCode)`, not on `measureCode` alone.
- **Inverted bounds (`lowerBound ≥ upperBound`).** Rejected with HTTP 422 and a clear
  message; persistence is refused.
- **Negative or zero unit.** Bounds may be negative (e.g., dBm power can be negative)
  but the `defaultUnit` string MUST be non-empty.
- **Soft-deleted template referenced by historical ticket measures.** The template
  remains retrievable via id (so source traceability survives) but is excluded from
  the default catalog listing and from the mandatory-coverage check of new tickets.
- **PosteType not in the enum.** Any request that targets a `PosteType` value
  outside the codified enum is rejected at the DTO validation layer with HTTP 400.
- **Empty optional fields (`antenna`, `frequencyMhz`, `modulationScheme`).** These
  remain `null`; they are not coerced to empty strings or zeros, because the workflow
  guard distinguishes "not applicable" from "applies and is zero."
- **Unauthenticated request.** Any catalog endpoint that requires authentication
  returns HTTP 401 when no valid token is provided.
- **Concurrent batch and single-insert with the same code.** The second one to
  commit fails with HTTP 409; partial state is impossible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST persist a catalog of measure templates, each template
  bound to a `PosteType` from the existing codified enum.
- **FR-002**: Each measure template MUST carry, at minimum: a `measureCode`, a
  `measureLabel`, a `category` (one of `POWER`, `VOLTAGE`, `CURRENT`, `FREQUENCY`,
  `TIME`, `TEMPERATURE`, `PER`, `RSSI`, `EVM`, `OTHER`), a `defaultUnit`, a numeric
  `defaultLowerBound`, a numeric `defaultUpperBound`, a `mandatory` boolean, and a
  `displayOrder` integer.
- **FR-003**: Each measure template MAY carry optional context: `antenna`,
  `frequencyMhz`, `modulationScheme`. These fields MUST remain `null` when not
  provided (never coerced to empty/zero).
- **FR-004**: The pair `(posteType, measureCode)` MUST be unique among **active**
  templates. Attempts to create a second active template with the same pair MUST
  fail with HTTP 409.
- **FR-005**: The system MUST reject any template where `defaultLowerBound` is
  greater than or equal to `defaultUpperBound`, with HTTP 422.
- **FR-006**: The system MUST expose a read endpoint that returns all templates,
  optionally filtered by `posteType` and optionally including soft-deleted entries
  via an `includeInactive` flag (default `false`).
- **FR-007**: The system MUST expose a read endpoint that returns the full template
  list for a single `PosteType`, sorted by `displayOrder` ascending.
- **FR-008**: The system MUST expose a read endpoint that returns just the measure
  templates (without parent metadata) for a single `PosteType`, suitable for
  downstream backend consumption.
- **FR-009**: The system MUST expose a read endpoint that retrieves a single template
  by its identifier and return HTTP 404 if not found.
- **FR-010**: The system MUST expose a create endpoint for a single template,
  restricted to roles `ADMIN_IT` and `CHEF_SECTEUR`; all other authenticated roles
  MUST receive HTTP 403.
- **FR-011**: The system MUST expose an update endpoint that allows modifying any
  field of an existing template except `posteType` and `measureCode` (those are
  immutable; changes are achieved by soft-delete + create), restricted to
  `ADMIN_IT` and `CHEF_SECTEUR`.
- **FR-012**: The system MUST expose a soft-delete endpoint that marks a template as
  inactive (`active = false`) without physically deleting the row, restricted to
  `ADMIN_IT` and `CHEF_SECTEUR`.
- **FR-013**: The system MUST expose a batch-create endpoint that accepts an array of
  templates for a single `PosteType` and either persists all of them atomically or
  rejects the entire batch with HTTP 422 / 409 (depending on the violation type),
  restricted to `ADMIN_IT` and `CHEF_SECTEUR`.
- **FR-014**: On application startup against a fresh database, the system MUST seed
  the catalog with the reference templates for `TEST_FONCTIONNEL` (≥ 6 measures),
  `WIFI_CONDUIT` (≥ 16 measures), and `ACC` (≥ 14 measures), derived from the three
  supervisor-provided production logs.
- **FR-015**: The seed MUST be idempotent: re-running it against an already-seeded
  database MUST NOT create duplicates and MUST NOT raise an error.
- **FR-016**: All read endpoints MUST be accessible to any authenticated user
  regardless of role (downstream services and curators all need to read).
- **FR-017**: All endpoints MUST require authentication; unauthenticated requests
  MUST receive HTTP 401.
- **FR-018**: Validation errors (missing required fields, invalid enum values,
  inverted bounds) MUST return HTTP 422 with a body that names the offending fields
  and the rule violated.
- **FR-019**: The catalog persistence layer MUST be created and evolved through
  **Flyway** SQL migrations stored under `src/main/resources/db/migration/` and
  named with the `V<major>.<minor>__<description>.sql` convention. Hibernate's
  `spring.jpa.hibernate.ddl-auto` MUST be switched to `validate` so Flyway owns the
  schema; ad-hoc DDL and `ddl-auto=update` are forbidden for any table introduced
  by this phase and all subsequent refactor phases.
- **FR-020**: The system MUST expose JPA entities only as DTOs at the HTTP boundary,
  with separate request and response shapes, in accordance with Principle VI of the
  project constitution.
- **FR-021**: The system MUST support a "list all postes that have at least one
  active template" read, enabling downstream services to enumerate populated postes
  without scanning the full catalog.
- **FR-022**: Every catalog read response that includes a measure template MUST
  surface the current `mandatory` flag (and the current `defaultLowerBound`,
  `defaultUpperBound`, `defaultUnit`) explicitly, so that downstream callers — in
  particular the ticket creation flow in Phase 002 — can snapshot those values onto
  the ticket at the moment of ticket creation. Catalog template updates after a
  ticket is opened MUST NOT retroactively alter that ticket's snapshot; Phase 003's
  workflow guard MUST read the per-ticket snapshot rather than the live catalog.
- **FR-023**: Every measure template MUST carry audit columns `createdAt`,
  `createdBy`, `updatedAt`, `updatedBy`, populated automatically by Spring Data JPA
  auditing wired to `SecurityUtils.getCurrentUserId()` for the actor columns and
  `Instant.now()` for the timestamps. Soft-delete (FR-012) MUST update `updatedAt`
  and `updatedBy`. All four audit fields MUST be surfaced on read response DTOs and
  MUST NOT be settable via request DTOs (server-controlled only).

### Key Entities

- **MeasureCategory (value list)**: The classification of a measure's physical
  quantity. Values: `POWER`, `VOLTAGE`, `CURRENT`, `FREQUENCY`, `TIME`,
  `TEMPERATURE`, `PER`, `RSSI`, `EVM`, `OTHER`. Used to drive future UI styling
  (icons/colors) and KPI grouping.

- **MeasureStatus (value list)**: The three-valued outcome state of a measure on a
  ticket. Values: `OK` (0), `OUT_OF_RANGE` (1), `NOT_EXECUTED` (2). Introduced here
  because the catalog phase publishes the enum that later phases (002, 003, 005) will
  consume on `ValidationMeasure`. Not persisted on the catalog itself.

- **PosteMeasureCatalog**: A reference template describing one expected measure for
  one `PosteType`. Key attributes: identifier, the parent `PosteType`, the measure
  code (industrial nomenclature, e.g., `POWER_RMS_AVG_VSA1`, `MES_BNFT_PWR0_2G`,
  `M_FXS_TRANS_FXS1_1000HZ`), a human-readable label, the `MeasureCategory`, the
  default unit (e.g., `dBm`, `mA`, `V`, `dB`, `°C`, `s`, `MHz`, `%`), the default
  lower and upper tolerance bounds, the `mandatory` flag (whether the workflow guard
  in Phase 003 will require this measure for transition), a `displayOrder` integer
  for stable presentation, an `active` boolean for soft delete, and optional
  contextual fields `antenna`, `frequencyMhz`, `modulationScheme` for RF measures.
  Conceptually owned by the `PosteType` and consumed by every later phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a fresh startup, the catalog returns at least 36 active measure
  templates spread across the three seeded postes (`TEST_FONCTIONNEL` ≥ 6,
  `WIFI_CONDUIT` ≥ 16, `ACC` ≥ 14), with 100 % of those templates carrying
  non-null bounds, unit, and `mandatory` flag.
- **SC-002**: Read of any single poste's catalog returns within 200 ms at the 95th
  percentile against a database containing up to 1 000 templates.
- **SC-003**: 100 % of attempted duplicate inserts (same `posteType + measureCode`
  pair, batch or single) are rejected with HTTP 409 and zero duplicate rows
  persisted, verified by automated tests.
- **SC-004**: 100 % of mutating requests from non-curator roles (`EXPERT`,
  `TECH_PREP`, `TECH_VAL`, `RESPONSABLE`) are rejected with HTTP 403, verified by
  automated tests covering each role.
- **SC-005**: The application starts cleanly against an already-seeded database with
  the same row count as before startup (idempotent seed), verified by an
  integration test that restarts the context.
- **SC-006**: A new poste can be fully catalogued (12 templates) by an `ADMIN_IT`
  using only the public API in under 5 minutes end-to-end (single batch call), with
  the resulting templates immediately consumable by a downstream read.
- **SC-007**: The HTTP contract (paths, request DTOs, response DTOs, status codes)
  is published in the Swagger UI of the running application and matches the spec
  exactly — verified by a contract test that exercises every endpoint.
- **SC-008**: Soft-deleted templates remain retrievable by id (so historical
  references survive) but are absent from the default catalog listing, verified
  by automated tests.

## Assumptions

- **Backend-only delivery.** Per explicit user constraint, the Angular admin page and
  shared `MeasureBadge` component listed in `Plan.md` §6 "Frontend deliverables" are
  out of scope for this phase. They will be implemented by a separate frontend
  project that consumes the REST contract specified here.
- **PosteType enum is frozen for this phase.** The `PosteType` Java enum (and its 22
  constants) is treated as immutable. Adding a new poste means a code change in a
  later, separate task, not part of this catalog phase.
- **Schema migrations use Flyway.** This phase introduces Flyway to the project (no
  migration tool was previously wired up). `spring.jpa.hibernate.ddl-auto` flips
  from `update` to `validate` so Flyway becomes the sole owner of schema evolution.
  The catalog DDL ships as `V1.1__poste_catalog.sql` and the reference seed as
  `V1.2__seed_poste_catalog.sql`. Switching the existing tables under Flyway control
  is handled by an initial baseline migration (`V1.0__baseline.sql`) generated from
  the current Hibernate-produced schema; this is a planning task, not a spec
  requirement beyond FR-019.
- **Authentication is reused.** The existing Keycloak OAuth2 JWT setup, the
  `KeycloakJwtConverter`, and the `ROLE_*` mapping documented in the root `CLAUDE.md`
  are reused as-is. No new authentication or authorization mechanism is introduced.
- **Initial seed sourced from supervisor logs.** The exact bounds, units, and
  mandatory flags of the seeded `TEST_FONCTIONNEL`, `WIFI_CONDUIT`, and `ACC`
  templates are derived from the three production log files committed under the
  Phase 004 fixtures path (`src/test/resources/fixtures/sagemcom-logs/`). Where the
  log shows only a measured value without explicit `[min, max]` bounds, the seed
  inserts the row with `active=false` and placeholder bounds, and includes an inline
  SQL comment naming the source log and the placeholder rule used. A curator must
  later enter engineering-validated bounds and flip the template to `active=true`
  before it participates in any ticket — no synthetic bounds ever drive a
  conformity verdict or a KPI deviation.
- **Catalog is read-heavy.** Read traffic is dominated by downstream services (every
  ticket creation, every workflow-guard check, every KPI rollup will read the
  catalog). Writes are infrequent (manual curation). Caching can be added later if
  measurements justify it; not in scope for this phase.
- **The legacy `ValidationResult` schema is untouched in this phase.** This phase
  introduces the catalog only; the consuming `ValidationMeasure` entity, its
  deviation calculator, and the migration of legacy results land in Phase 002.
