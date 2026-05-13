# Implementation Plan: Workflow Guard (Backend Only)

**Branch**: `003-workflow-guard` | **Date**: 2026-05-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-workflow-guard/spec.md`

## Summary

Introduce a single `TicketTransitionGuard` that gates the `EN_COURS → EN_REVUE` transition behind a coverage rule: a ticket cannot move to review unless every mandatory measure on it has a status other than `NOT_EXECUTED`. The guard exposes a non-mutating `GET /api/validations/{id}/readiness` probe that returns the same `WorkflowReadinessDTO` payload the guard returns when it blocks a transition (HTTP 422 — Constitution IV). Every measure mutation publishes a fresh readiness snapshot to a per-ticket STOMP topic so the frontend can live-update the submit button without polling.

Technical approach (resolved by `/speckit-clarify` 2026-05-12 and Phase 0 below):

- The `mandatory` flag is read from a per-measure snapshot (`ValidationMeasure.mandatoryAtCreation`) — not the live catalog (Q1, Phase 001 R-005). Phase 002 did **not** add this column, so this plan owns the migration that adds it and backfills existing rows from `validation_measures.catalog_template_id → poste_measure_catalog.mandatory` (with a `false` default for catalog-less ad-hoc measures from FR-008/Phase 002).
- `MeasureStatus` vocabulary is `OK` / `OUT_OF_RANGE` / `NOT_EXECUTED` verbatim from Phase 002 (Q2). "Filled" = `status != NOT_EXECUTED`; `outOfRangeMeasures` = `status == OUT_OF_RANGE`.
- Refactor scope is **delegate-and-wrap** (Q5): only `MandatoryMeasureCoverageRule` is implemented as a `TransitionRule` strategy this phase. Existing role/prep/handover checks stay where they are and are invoked from the guard via thin adapter calls so their refusal payloads route through the same 422+`WorkflowReadinessDTO` envelope.
- Readiness probe authorization mirrors the ticket-detail endpoint 1:1 (Q4) — the same access check protects both the REST probe and STOMP subscriptions to `/topic/validation/{id}/readiness`.
- The guard MUST be invoked at **every** code path that sets `validation.status = EN_REVUE` (FR-009, SC-006). In the current codebase that means both `ValidationService.submitForReview(id)` (L422–L470) and the auto-advance branch in the poste-closure flow (L626–L644). The auto-advance path, when blocked by the guard, MUST log a warning and leave the ticket in `EN_COURS` rather than throw — this keeps the closure of one poste from cascading into a 422 the user did not ask for.
- Real-time delivery uses the existing STOMP infrastructure (Constitution X — no new transport). The publish hook lives in `ValidationMeasureServiceImpl` (after every successful create / update / delete / batch-create) and computes the snapshot via the same `WorkflowReadinessService` the REST probe uses, so the two delivery channels are byte-equivalent (FR-007 + SC-007).
- Out-of-range gating is explicitly **not** introduced (FR-008); it is closure-verdict logic deferred to Phase 005.
- Concurrency continues to follow last-writer-wins (Phase 002 Q4); no optimistic version column.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 4.0.2 (web-mvc, data-jpa, validation, security, oauth2-resource-server, websocket / STOMP), Lombok, PostgreSQL JDBC, Flyway Core + Flyway PostgreSQL (already on the path from Phases 001 / 002)
**Storage**: PostgreSQL 15+ database `sageLine_db` on `localhost:5432`. Existing tables only; this phase adds **one column** (`validation_measures.mandatory_at_creation BOOLEAN NOT NULL DEFAULT FALSE`) and one backfill statement. No new tables. No reads from `poste_measure_catalog` at runtime once the snapshot is populated (per Q1).
**Testing**: JUnit 5, Spring Boot Test (`@SpringBootTest`, `@DataJpaTest`, `MockMvc`), Spring Security Test (`@WithMockUser` / JWT mock), Testcontainers PostgreSQL for integration tests, AssertJ for fluent assertions. STOMP contract tests use `WebSocketStompClient` against the embedded broker (same pattern as the existing `WebSocketEventListener` tests).
**Target Platform**: Linux/Windows JVM — same deployable as the rest of SageLine
**Project Type**: Web service (Spring Boot monolith); backend-only delivery for this phase (frontend deliverables from `Plan.md` §8 are deferred per user instruction and per spec Assumptions)
**Performance Goals**: Readiness probe ≤ 300 ms p95 with ≤ 100 measures (SC-003). STOMP snapshot delivered after a measure mutation ≤ 500 ms p95 (SC-005). Submit-for-review (happy path) latency budget unchanged from Phase 002 (≤ 150 ms p95 for a single mutating call).
**Constraints**: Constitution IV (NON-NEGOTIABLE) — every transition behind a guard, blocked transitions return HTTP 422 with `WorkflowReadinessDTO`. Constitution VI — no JPA entity types in `@RestController` signatures. Constitution X — no AI dependencies. The guard is the single funnel; `validation.setStatus(EN_REVUE)` must not appear outside the guard's allow path (enforced by code-review checklist + a controller-side architecture test that fails if any non-guard caller writes that value).
**Scale/Scope**: ~hundreds of tickets per month × ≤ 100 measures each ⇒ readiness probe touches ≤ 100 rows; STOMP fanout is ≤ a handful of subscribers per ticket. Single-instance deploy; no horizontal-scaling or message-broker-clustering concerns.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version: **1.0.0** (file: `.specify/memory/constitution.md`).

| # | Principle | Applies? | Status | Notes |
|---|-----------|----------|--------|-------|
| I | Industrial Fidelity | ✅ | PASS | No new domain terms invented. `WorkflowReadinessDTO`, `mandatoryFilled`, `mandatoryTotal`, `missingMeasures`, `outOfRangeMeasures`, `canTransition`, `blockingReasons` are workflow-coverage vocabulary, not measure nomenclature. |
| II | Bounded Tolerance, Not Target | ➖ | N/A | This phase reads `MeasureStatus`; it does not introduce or migrate any tolerance values. |
| III | Three-Valued Measure Status | ✅ | PASS | Q2-clarified. The guard's "filled" predicate is `status != NOT_EXECUTED`, treating `NOT_EXECUTED` as the first-class workflow signal Constitution III requires. |
| IV | Guarded Transitions (NON-NEGOTIABLE) | ✅ | PASS | This phase is the materialization of Principle IV. `TicketTransitionGuard` is the single entry point, `MandatoryMeasureCoverageRule` is the first `TransitionRule`, blocked transitions return 422+`WorkflowReadinessDTO` per the principle's exact text. |
| V | Traceability from Log to Verdict | ➖ | N/A | Source-log fields (`sourceLogFile`, `enteredBy`) are read into `MissingMeasureDTO` for visibility but are not modified or extended. |
| VI | DTO / Entity Separation | ✅ | PASS | All endpoint signatures use DTOs (`WorkflowReadinessDTO`, `MissingMeasureDTO`, `OutOfRangeMeasureDTO`). Mapper in `mappers/WorkflowReadinessMapper`. No `@Entity` type leaks. |
| VII | Real-Log Test Fixtures | ➖ | N/A | This phase parses no log content. Integration tests reuse Phase 001's catalog seed and Phase 002's measure-instantiation flow as the input to the guard, but no `.log` file is read. |
| VIII | Backward Compatibility During Refactor | ✅ | PASS | The legacy `/api/validation-results` controller (deprecated by Phase 002) is untouched and continues to emit `Deprecation: true`. The existing `submit-review` endpoint URL and HTTP method are unchanged; only the response shape on the **failure** path becomes a `WorkflowReadinessDTO` (callers previously got a generic 4xx error message — none consume the failure-payload schema, so this is additive). |
| IX | Auditability of Overrides | ➖ | N/A | Override semantics belong to closure verdicts (Phase 005). No override path runs through the guard in this phase. |
| X | No Premature AI Integration | ✅ | PASS | No AI imports introduced. |
| XI | Frontend Stack Consistency | ➖ | N/A | Backend-only phase. |
| XII | Role-Gated UI (NON-NEGOTIABLE) | ✅ (backend half) | PASS | `@PreAuthorize` retained on `submit-review` (`TECH_VAL`/`ADMIN_IT`). Readiness probe gated by ticket-read access (Q4) — implemented as a service-layer access check that mirrors `ValidationService.findById`'s production-line scoping; controller carries `@PreAuthorize("isAuthenticated()")`. |

**Additional Constraints (Constitution §"Additional Constraints"):**

- **Layered structure** — new code follows Controller → Service → Repository. Guard, rules, and readiness service are service-layer beans; the controller is a thin pass-through.
- **Single source of truth for workflow refusal payload** — `WorkflowReadinessService` is the only producer of `WorkflowReadinessDTO`. Both the REST probe and the 422 refusal envelope are computed by the same method (`computeReadiness(validationId, targetStatus)`), guaranteeing SC-007.
- **Repository queries** — the readiness query MUST fetch the ticket's measures with a single JPQL using `LEFT JOIN FETCH catalog_template, entered_by` (avoid N+1). Existing `ValidationMeasureRepository` query from Phase 002 is reused with a coverage-projection variant added.
- **Per-measure snapshot column** — `validation_measures.mandatory_at_creation BOOLEAN NOT NULL DEFAULT FALSE` is added by `V3.0__validation_measure_mandatory_snapshot.sql`. Backfill: `UPDATE validation_measures vm SET mandatory_at_creation = COALESCE(c.mandatory, FALSE) FROM poste_measure_catalog c WHERE vm.catalog_template_id = c.id;`. Constitution VIII does not require a deprecation window — this is an additive nullable→default-true-default-false migration on a Phase 002 table that is itself only one phase old, and the column is *introduced* by this phase, not superseded.

**Result: Constitution Check PASSES.** No deviations to declare; `Complexity Tracking` section omitted.

## Project Structure

### Documentation (this feature)

```text
specs/003-workflow-guard/
├── plan.md              # This file
├── spec.md              # Feature spec (with Clarifications session 2026-05-12, 5 Qs)
├── research.md          # Phase 0 output — guard architecture, snapshot-column migration, STOMP topic, auto-advance behavior, 422 mapping
├── data-model.md        # Phase 1 output — WorkflowReadinessDTO + supporting DTOs + ValidationMeasure column addition + transition state edges
├── quickstart.md        # Phase 1 output — curl + STOMP recipe: probe a blocked ticket → record missing measure → observe snapshot → submit succeeds
├── contracts/
│   └── workflow-guard-api.openapi.yaml   # OpenAPI 3.0 fragment for readiness probe + submit-review 422 schema; STOMP topic documented inline
├── checklists/
│   └── requirements.md  # Spec-quality checklist (created by /speckit-specify)
└── tasks.md             # Phase 2 output — created by /speckit-tasks (not yet)
```

### Source Code (repository root)

The existing Spring Boot monolith. New files land under `com.pfe.sageline.*` and `src/main/resources/db/migration/`. No new modules.

```text
sageLine-backend/
├── src/
│   ├── main/
│   │   ├── java/com/pfe/sageline/
│   │   │   ├── controller/
│   │   │   │   ├── WorkflowReadinessController.java        # NEW — GET /api/validations/{id}/readiness?targetStatus=...
│   │   │   │   └── ValidationController.java               # EXISTING — submitForReview wired to invoke TicketTransitionGuard before status mutation
│   │   │   ├── service/
│   │   │   │   ├── workflow/
│   │   │   │   │   ├── TicketTransitionGuard.java          # NEW — single entry point: check(ticket, targetStatus) → ReadinessOutcome
│   │   │   │   │   ├── TransitionRule.java                 # NEW — interface: evaluate(ticket, targetStatus) → RuleVerdict
│   │   │   │   │   ├── RuleVerdict.java                    # NEW — record (boolean allowed, List<String> blockingReasons)
│   │   │   │   │   ├── MandatoryMeasureCoverageRule.java   # NEW — the only fresh rule this phase; reads mandatoryAtCreation snapshot
│   │   │   │   │   ├── SourceStatusRule.java               # NEW — refuses if validation.status != EN_COURS for target EN_REVUE
│   │   │   │   │   ├── LegacyChecksAdapter.java            # NEW — thin call-through to existing role/prep/handover checks per Q5 delegate-and-wrap
│   │   │   │   │   └── WorkflowReadinessService.java       # NEW — single producer of WorkflowReadinessDTO (probe + 422 envelope + STOMP push)
│   │   │   │   ├── ValidationService.java                  # EXISTING — submitForReview() refactored: guard.check() before setStatus(EN_REVUE)
│   │   │   │   └── ValidationMeasureServiceImpl.java       # EXISTING — after each successful mutation, asks WorkflowReadinessService to publish snapshot
│   │   │   ├── repository/
│   │   │   │   └── ValidationMeasureRepository.java        # EXISTING — adds JPQL projection: countByValidationIdAndMandatoryAtCreationGroupedByStatus
│   │   │   ├── entity/
│   │   │   │   └── ValidationMeasure.java                  # EXISTING — adds field `private boolean mandatoryAtCreation;` mapped to new column
│   │   │   ├── dtos/
│   │   │   │   ├── request/
│   │   │   │   │   └── (none — readiness probe is GET-only with a query param)
│   │   │   │   └── response/
│   │   │   │       ├── WorkflowReadinessDTO.java           # NEW — see data-model.md
│   │   │   │       ├── MissingMeasureDTO.java              # NEW — { measureCode, label, required }
│   │   │   │       └── OutOfRangeMeasureDTO.java           # NEW — { measureCode, label, measuredValue, lowerBound, upperBound, deviationPct }
│   │   │   ├── mappers/
│   │   │   │   └── WorkflowReadinessMapper.java            # NEW — projection rows → DTOs
│   │   │   ├── exception/
│   │   │   │   ├── TransitionBlockedException.java         # NEW — carries WorkflowReadinessDTO; mapped to 422 by GlobalExceptionHandler
│   │   │   │   └── GlobalExceptionHandler.java             # EXISTING — +mapping for TransitionBlockedException → 422 with DTO body
│   │   │   └── Config/
│   │   │       └── WebSocketConfig.java                    # EXISTING — `/topic/validation.{id}.readiness` falls under existing `/topic` prefix; no config change needed; documented in research.md
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V3.0__validation_measure_mandatory_snapshot.sql  # NEW — adds mandatory_at_creation column + backfill from poste_measure_catalog.mandatory
│   └── test/
│       ├── java/com/pfe/sageline/
│       │   ├── controller/
│       │   │   ├── WorkflowReadinessControllerTest.java                # NEW — MockMvc: 200 happy path, 200 zero-mandatory, 403 cross-line access, default-target echo
│       │   │   └── ValidationControllerSubmitReviewTest.java           # NEW — MockMvc: 200 happy path, 422+DTO blocked path, 422 wrong-source-status
│       │   ├── service/workflow/
│       │   │   ├── TicketTransitionGuardTest.java                      # NEW — pure unit: rule sequencing, allow/block aggregation, multiple-blocking-reasons
│       │   │   ├── MandatoryMeasureCoverageRuleTest.java               # NEW — pure unit: snapshot-based filled-vs-total counting incl. ad-hoc (catalog-less) measures
│       │   │   └── WorkflowReadinessServiceTest.java                   # NEW — @SpringBootTest + Testcontainers: probe ≡ refusal envelope (SC-007); idempotent reads (SC-004); 100-measure latency assertion (SC-003 sample)
│       │   ├── integration/
│       │   │   ├── SubmitReviewLifecycleIntegrationTest.java           # NEW — 14/16 → 422; record 2 → 200; matches Story 1 + Story 2 + SC-008
│       │   │   ├── AutoAdvanceGuardedIntegrationTest.java              # NEW — poste-closure auto-advance is blocked when coverage missing; ticket stays EN_COURS; warning logged
│       │   │   └── ReadinessSnapshotStompContractTest.java             # NEW — WebSocketStompClient subscribes to /topic/validation.{id}.readiness; create/update/delete each fire exactly one snapshot; cross-ticket isolation per FR-011
│       │   └── migration/
│       │       └── ValidationMeasureMandatorySnapshotMigrationTest.java # NEW — seeds rows with catalog_template_id, runs Flyway, asserts mandatory_at_creation matches catalog.mandatory; ad-hoc rows default to false
│       └── resources/
│           └── (no new fixtures)
```

**Structure Decision**: Single Spring Boot project; no module split. Workflow-related beans live in a new `service/workflow/` sub-package so the strategy classes (`TransitionRule` + concrete rules) can be browsed as a unit and so future phases that add transitions plug into the same package without bloating the top-level `service/` namespace. The guard, readiness service, rule interface, and one concrete rule are split into separate small classes specifically so each is unit-testable in isolation against the canonical Story 1 / Story 2 fixtures — the same decomposition pattern Phases 001 and 002 used.

## Complexity Tracking

> Constitution Check passes. No violations. This section intentionally empty.
