# Research — Phase 002 ValidationMeasure Refactor

All `NEEDS CLARIFICATION` markers from the spec were resolved during `/speckit-clarify` (session 2026-05-11). This file captures the additional design-level research necessary before Phase 1 design.

## R1 — Deviation algorithm and edge-case handling

**Decision.** Compute `status` and `deviationPct` in a single component, `MeasureDeviationCalculator`, using:

```
center    = (lowerBound + upperBound) / 2
halfRange = (upperBound - lowerBound) / 2
deviation = abs(measured - center) / halfRange * 100      ; only if measured != null
status    = OK              if  lowerBound <= measured <= upperBound
            OUT_OF_RANGE    otherwise
            NOT_EXECUTED    if  measured is null
```

This is a *continuous* formula: a value at the center yields 0%, a value at either bound yields 100%, and a value outside the window simply exceeds 100% with no discontinuity. Canonical fixtures: `15.5 on [13.5,16.5] → 33.33%`; `20.0 on [13.5,16.5] → 333.33%`; `null → null`. The matching test must assert `333.33` (not `433.33`) — earlier drafts of `spec.md` quoted `≈ 433` in SC-001; that was a math error and is corrected.

with two edge guards:

- `halfRange == 0` (zero-width tolerance, `lowerBound == upperBound`) → reject the **input** at the DTO validator with HTTP 400. `MeasureDeviationCalculator` itself assumes `halfRange > 0` and treats zero-width as a programmer error (`IllegalStateException`). Rationale: zero-width tolerance is industrially meaningless; the catalog DTO validator already rejects it on Phase 001's create/update — reproducing the guard here protects ad-hoc measures.
- `measured == lowerBound` or `measured == upperBound` (boundary case) → `status = OK`, `deviationPct == 100.0`. This matches FR-018.

**Rationale.** A single pure component is unit-testable against the canonical SC-001 fixtures (`15.5 → OK, 33.3%`; `20.0 → OUT_OF_RANGE, 433%`; `null → NOT_EXECUTED`) and is the only path the service layer takes. Putting the math inline in setters would defeat the constitutional requirement that recomputation happens on every write (FR-004).

**Alternatives considered.**
- Hibernate `@PrePersist` / `@PreUpdate` lifecycle hook on the entity. Rejected: not unit-testable without an EntityManager, and Constitution VI prefers explicit service-layer behavior over entity-side magic that hides business rules.
- A `@Formula`-derived field. Rejected: persisting the computed value lets reporting and KPI queries (Phase 006) read it cheaply instead of recomputing on every read.

## R2 — Legacy data migration spread

**Decision.** Clarification Q5 → ±5% symmetric spread of `expectedValue`. When `expectedValue == 0`, use an absolute ±0.5 spread (in the legacy unit) to avoid a zero-width window. Migrated `status` is forced to match the legacy `conform` boolean (`true → OK`, `false → OUT_OF_RANGE`) regardless of where `measured_value` falls in the synthesized window; `deviationPct` is *still* computed against the synthesized window so it remains visually consistent with manually-entered measures.

**Migration is idempotent.** A `migrated_at` column on `validation_results` (added in V2.1) marks rows already copied; V2.2 only copies rows where `migrated_at IS NULL` and stamps them at the end of the migration. Migration ordering is V2.0 (DDL) → V2.1 (legacy marker column) → V2.2 (data migration + stamp) so the idempotency gate exists before the copy reads it.

**Signed-value bounds (post-implementation correction).** The naive expansion `[v*0.95, v*1.05]` produces an inverted window when `expected_value` is negative (common for dBm readings: `-50 * 0.95 = -47.5 > -50 * 1.05 = -52.5`), which violates `ck_vm_bounds`. The migration SQL therefore wraps each bound in `LEAST(...)` / `GREATEST(...)` so the window is correctly oriented regardless of sign. This applies to both the bound columns *and* the deviation arithmetic that references them.

**Rationale.** Forcing status from `conform` preserves historical truth: if the legacy row says "operator marked it conforming", the migrated measure shows `OK` even when the synthesized window is technically too narrow to contain the recorded value. Without this override, every legacy `conform=true` row with non-trivial drift would post-migrate as `OUT_OF_RANGE` and confuse reviewers.

**Alternatives considered.**
- Drop legacy and start fresh. Rejected: violates Constitution VIII (one-phase backward-compat minimum) and loses historical KPI continuity.
- Migrate with `lowerBound = upperBound = expectedValue` and skip computation. Rejected: produces zero-width windows that crash deviation math on later reads.

## R3 — Legacy controller deprecation signal

**Decision.** Implement `LegacyResultsDeprecationFilter` as a `OncePerRequestFilter` that matches `/api/validation-results/**` and sets `Deprecation: true` on the response *before* the controller writes. Register it in `Config/` next to `KeycloakConfig`.

**Rationale.** Adding the header at the filter level keeps the legacy controller code itself untouched (Constitution VIII is satisfied without bit-rotting code that's about to be removed). The filter is stateless, side-effect-free, and trivial to unit-test against the canonical legacy endpoints.

**Alternatives considered.**
- `@ResponseHeader` annotation on each legacy controller method. Rejected: requires editing the legacy controller for a behavior the spec frames as a transitional shim.
- Reverse proxy header injection. Rejected: the project deploys as a single Spring Boot jar with no proxy in the default dev/test environment; tests would not see the header.

## R4 — Concurrency strategy

**Decision.** Last-writer-wins (clarification Q4). No `@Version` column on `ValidationMeasure`. Every write refreshes `entered_by` and `measured_at`, which the read endpoint surfaces; this is the audit trail.

**Rationale.** Mutations are restricted to `EN_COURS` (clarification Q2) and to `TECH_VAL` / `TECH_PREP` / `ADMIN_IT` (clarification Q3). The handover protocol (existing `HandoverService`) ensures only one technician owns the ticket at a time. Optimistic locking would inflate the API surface (every client threads a version token) for a contention pattern that does not exist in practice.

**Alternatives considered.**
- Optimistic locking with `@Version`. Rejected for the reasons above; can be added later without re-migrating data (adding a `@Version` column is backward-compatible if defaulted to `0`).
- Pessimistic row-lock per measure. Rejected: requires holding a DB lock across the HTTP request, which is fragile under network hiccups and unnecessary for the contention profile.

## R5 — Batch endpoint payload shape (clarification Q1 implementation)

**Decision.** `POST /api/validations/{id}/measures/batch` accepts `{"measures": [...]}`. On any entry failing pre-flight validation (DTO-level, ticket-status check via `MeasureEditabilityGuard`, uniqueness check, FK existence check), the whole batch aborts and the response is HTTP 422 with body:

```json
{
  "type": "BATCH_REJECTED",
  "totalEntries": 5,
  "failedEntries": [
    { "index": 2, "code": "UNKNOWN_TEMPLATE", "message": "templateId 999 not found in catalog" },
    { "index": 4, "code": "DUPLICATE_MEASURE_CODE", "message": "measure_code MES_BNFT_PWR0_2G already exists on this ticket" }
  ]
}
```

A successful batch returns HTTP 201 with the list of created `ValidationMeasureResponse` objects.

**Rationale.** Per-entry diagnostics with `index` lets the client highlight which row in their form to fix without re-parsing the whole submission. The transactional guarantee falls out naturally from a single `@Transactional` service method that throws `BatchMeasureValidationException` if any entry fails — Spring's rollback handles the rest.

**Alternatives considered.**
- Returning HTTP 400 with the same body. Rejected: HTTP 422 is the conventional "semantically invalid but syntactically valid" status; the project already uses 422 for the workflow-readiness block (Phase 003), and reusing it keeps client error-handling uniform.
- Returning `207 Multi-Status` with per-entry outcomes. Rejected by clarification Q1 (all-or-nothing).

## R6 — Instantiate-from-catalog idempotency

**Decision.** `POST /api/validations/{id}/measures/from-template` (no body) calls a service method that:

1. Loads the ticket's zone → poste type.
2. Loads all `active = true` rows from `PosteMeasureCatalog` for that poste type.
3. Loads the *existing* `validation_measures` rows on this ticket keyed by `(measure_code, antenna, frequency_mhz, modulation_scheme)`.
4. For each catalog row not already present on the ticket: inserts a new `ValidationMeasure` with `measured_value = NULL`, `status = NOT_EXECUTED`, `deviation_pct = NULL`, all other fields copied from the template.

Re-invocation creates zero new rows when the previous invocation already covered the catalog (FR-010 idempotency; SC-005).

**Rationale.** Keying idempotency by the same uniqueness tuple the DB enforces (R7 below) makes the application-level check and the DB constraint consistent: even if a race lets two callers run step 4 simultaneously, the unique index catches the duplicate and the transaction rolls back; the caller retries and finds the row already present.

## R7 — Uniqueness for catalog-instantiated vs. ad-hoc measures

**Decision.** A unique index on `validation_measures` over `(validation_id, measure_code, COALESCE(antenna,''), COALESCE(frequency_mhz,-1), COALESCE(modulation_scheme,''))`. Catalog-linked rows inherit the four trailing components from their template; ad-hoc rows carry whatever the caller supplied (or NULLs coerced to the sentinel values).

**Rationale.** Without the `COALESCE`-based key, PostgreSQL's NULL-comparison semantics would let `(ticket=42, code='X', antenna=NULL)` exist multiple times. With it, NULL is treated as a distinct "absent" value uniformly.

**Alternatives considered.**
- A surrogate uniqueness column populated at insert time. Rejected: adds storage with no benefit over the index expression.
- Application-level uniqueness check only. Rejected: races between concurrent inserts (R4) would slip duplicates through.

## R8 — Source-log file column reservation

**Decision.** `source_log_file VARCHAR(255) NULL` is added to `validation_measures` in V2.0, but only Phase 004's importer ever writes to it. Phase 002 endpoints accept the field on the request DTOs (for round-tripping legacy migration values and to make Phase 004 a pure consumer of an already-shipped contract) but reject any value supplied by a client other than `null` on **create** and **update** in this phase. Migration may set it; HTTP clients may not.

**Rationale.** Per FR-013 the placeholder must exist now to avoid re-migration later. Locking write access until Phase 004 lands prevents callers from forging false provenance.

**Alternatives considered.**
- Add the column in Phase 004's migration. Rejected: a second migration on a high-row table is more costly than reserving the column up front.
- Allow clients to write `source_log_file` in Phase 002. Rejected: provenance must come from a verified upload, not a free-form string.

## R9 — Audit fields strategy

**Decision.** Reuse the `AuditorAware<Long>` bean introduced in Phase 001. `entered_by` is mapped from `@CreatedBy` on insert and **also** refreshed on update (custom `@PrePersist` / `@PreUpdate` handler that copies `auditorAware.currentAuditor` into `entered_by` on every write — this matches FR-005 and the last-writer-wins audit trail decided in R4). `measured_at` is mapped to `@LastModifiedDate` and refreshes on every write.

**Rationale.** Standard Spring Data JPA `@CreatedBy` only stamps on insert. The clarified last-writer-wins semantics require `entered_by` to follow the most recent writer; we get this with a tiny custom listener that asks the existing `AuditorAware` bean for the current user on every save. No new infrastructure.

**Alternatives considered.**
- Manually set `entered_by` in the service layer. Rejected: spreads audit logic outside the audit listener and makes accidental omissions easy.
- Add a separate `last_modified_by` column. Rejected: introduces two operator columns where the spec only describes one — would force a DTO change later.

## Open items deferred to Phase 003+

- The workflow-coverage guard (Phase 003) will be the consumer of `MeasureStatus != NOT_EXECUTED` counts on a ticket. Phase 002 publishes the data; Phase 003 builds the guard.
- WebSocket push of a per-ticket readiness snapshot on every measure mutation is **Phase 003**, not 002. Phase 002 does not emit STOMP messages.
- Physical removal of `validation_results` is **Phase 005** at the earliest (Constitution VIII minimum window).
