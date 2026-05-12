# Implementation Plan: ValidationMeasure Refactor (Backend Only)

**Branch**: `002-validation-measure-refactor` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-validation-measure-refactor/spec.md`

## Summary

Replace the legacy generic `ValidationResult` (`parameter` / `measuredValue` / `expectedValue` / `conform`) with a new industrial `ValidationMeasure` aligned to the bounded-tolerance, status-aware Sagemcom model introduced by Phase 001. Each measure carries `[lowerBound, upperBound]` against a unit, a three-valued `MeasureStatus`, a computed `deviationPct`, optional physical context (antenna, frequency, modulation), traceability fields (operator, timestamp, `sourceLogFile`), and an optional link to a `PosteMeasureCatalog` template.

Technical approach (resolved by `/speckit-clarify` and Phase 0 below):

- New JPA entity `ValidationMeasure` + repository + service + controller + DTO/mapper under existing `com.pfe.sageline.*` packages.
- `MeasureDeviationCalculator` (new component) is the single source of truth for `status` and `deviationPct`. Recomputed on every service-layer create/update.
- Flyway migrations: `V2.0__validation_measure.sql` (DDL), `V2.1__validation_measure_data_migration.sql` (legacy → new, idempotent), `V2.2__validation_results_legacy_marker.sql` (adds a `migrated_at` audit column on the legacy table — **does not drop it**, per Constitution VIII).
- Six write endpoints + one read endpoint under `/api/validations/{id}/measures/**`, all `@PreAuthorize`-gated to `TECH_VAL`/`TECH_PREP`/`ADMIN_IT`; reads open to any authenticated user with access to the ticket.
- Batch endpoint is transactional all-or-nothing per clarification Q1; rejected payload surfaces per-entry diagnostics via a `BatchMeasureValidationException` mapped to HTTP 422 by `GlobalExceptionHandler`.
- Ticket-status edit rule (`EN_COURS` only — clarification Q2) enforced in a single `MeasureEditabilityGuard` checked at the top of every mutating service method.
- Concurrency: last-writer-wins (clarification Q4); no optimistic version column introduced.
- Legacy `/api/validation-results` controller stays in place; a `Deprecation: true` HTTP header is injected via a one-line `OncePerRequestFilter` (`LegacyResultsDeprecationFilter`) scoped to that controller's URL pattern.
- Real-log integration fixtures committed in Phase 001 (`src/test/resources/fixtures/sagemcom-logs/`) are not parsed in this phase, but Phase 002's integration tests use them to anchor catalog-instantiation assertions (the catalog rows whose source codes appear in the BWC log become `NOT_EXECUTED` measures on a `WIFI_CONDUIT` ticket).

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 4.0.2 (web-mvc, data-jpa, validation, security, oauth2-resource-server), Lombok, PostgreSQL JDBC, Flyway Core + Flyway PostgreSQL (already on the path from Phase 001)
**Storage**: PostgreSQL 15+ database `sageLine_db` on `localhost:5432`; new table `validation_measures`; existing `validation_results` retained read-only behind a deprecated controller
**Testing**: JUnit 5, Spring Boot Test (`@SpringBootTest`, `@DataJpaTest`, `MockMvc`), Spring Security Test (`@WithMockUser` / JWT mock), Testcontainers PostgreSQL for integration tests against a real DB. AssertJ for fluent assertions, including float tolerance (`isCloseTo`) on `deviationPct`.
**Target Platform**: Linux/Windows JVM — same deployable as the rest of SageLine
**Project Type**: Web service (Spring Boot monolith); backend-only delivery for this phase (frontend deliverables from Plan.md §7 are deferred per user instruction)
**Performance Goals**: Read of one ticket's measures ≤ 300 ms p95 with ≤ 100 measures (SC-006). Batch create of ≤ 20 entries ≤ 500 ms p95. Single mutating call ≤ 150 ms p95.
**Constraints**: Strict adherence to Constitution Principle VI (no JPA entity in REST). `MeasureDeviationCalculator` is the only path to compute `status`/`deviationPct` (no inline computation in controllers, mappers, or callers — Constitution §"Additional Constraints"). Legacy `validation_results` table MUST remain queryable; physical drop deferred until Phase 005 closure (Constitution VIII).
**Scale/Scope**: ~22 poste types × ~10–20 catalog templates each, instantiated on ~hundreds of tickets per month ⇒ ≤ low-thousands of `validation_measures` rows steady-state during the academic project lifetime; single-instance deploy; no horizontal scaling concern.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version: **1.0.0** (file: `.specify/memory/constitution.md`).

| # | Principle | Applies? | Status | Notes |
|---|-----------|----------|--------|-------|
| I | Industrial Fidelity | ✅ | PASS | `measure_code`, units, and context fields (`antenna`, `frequency_mhz`, `modulation_scheme`) match Sagemcom log nomenclature. No invented terms. |
| II | Bounded Tolerance, Not Target | ✅ | PASS | Entity carries `lower_bound` + `upper_bound`; the legacy `expected_value` is migrated *into* a tolerance window (±5%, clarification Q5) and never persisted on the new entity. |
| III | Three-Valued Measure Status | ✅ | PASS | `MeasureStatus` (introduced Phase 001) persisted on every row. Legacy boolean `conform` is mapped on migration but never reintroduced. |
| IV | Guarded Transitions | ➖ | DEFERRED | Workflow coverage guard is Phase 003. This phase persists the data the guard will consume; it does not introduce transitions. The `MeasureEditabilityGuard` introduced here is a *data-mutation* guard (not a status-transition guard) and follows the same single-entry-point pattern. |
| V | Traceability from Log to Verdict | ✅ | PASS | Every row carries `entered_by` + `measured_at`; `source_log_file` column reserved and nullable (populated by Phase 004 importer). Manual entries leave `source_log_file = NULL`; the field's null-vs-not-null distinction documents the origin. |
| VI | DTO / Entity Separation | ✅ | PASS | Controller signatures use `CreateMeasureRequest` / `UpdateMeasureRequest` / `BatchCreateMeasureRequest` / `ValidationMeasureResponse`. Mapper in `mappers/ValidationMeasureMapper`. No `@Entity` type leaks. |
| VII | Real-Log Test Fixtures | ✅ | PASS | Three supervisor logs committed in Phase 001 are reused by an integration test that asserts catalog-instantiated rows match the canonical bounded-tolerance fixtures (SC-001). Negative fixtures (corrupted, unknown station) are Phase 004's responsibility. |
| VIII | Backward Compatibility During Refactor | ✅ | PASS | Legacy `/api/validation-results` controller stays operational; `Deprecation: true` header injected via filter; `validation_results` table preserved with a `migrated_at` audit column (V2.2). Removal deferred to Phase 005 (constitutional minimum: one phase). |
| IX | Auditability of Overrides | ➖ | N/A | Override semantics belong to conformity verdicts (Phase 005). Measure mutations are still audit-stamped (`entered_by`, `measured_at`). |
| X | No Premature AI Integration | ✅ | PASS | No AI imports introduced. |
| XI | Frontend Stack Consistency | ➖ | N/A | Backend-only phase. |
| XII | Role-Gated UI | ✅ (backend half) | PASS | `@PreAuthorize` per FR-016: mutating endpoints require `TECH_VAL`, `TECH_PREP`, or `ADMIN_IT`; reads require authentication only. |

**Additional Constraints (Constitution §"Additional Constraints"):**

- `MeasureDeviationCalculator` is introduced **here**, in this phase, as the single source of truth for `status` and `deviationPct` — recomputed on every insert/update (FR-004). Direct field assignment from controllers, mappers, or DTO setters is forbidden by code review.
- Repository queries use JPQL with `LEFT JOIN FETCH` on `catalog_template` and `entered_by` for the list endpoint (avoid N+1).
- Unique constraint on `(validation_id, measure_code, COALESCE(antenna,''), COALESCE(frequency_mhz,-1), COALESCE(modulation_scheme,''))` enforced as a unique index — duplicate inserts return HTTP 409. (`antenna`, `frequency_mhz`, `modulation_scheme` differentiate same-code RF measures across physical contexts.)

**Result: Constitution Check PASSES.** No deviations to declare; `Complexity Tracking` section omitted.

## Project Structure

### Documentation (this feature)

```text
specs/002-validation-measure-refactor/
├── plan.md              # This file
├── spec.md              # Feature spec (with Clarifications session 2026-05-11, 5 Qs)
├── research.md          # Phase 0 output — deviation algorithm, migration spread, deprecation filter, concurrency
├── data-model.md        # Phase 1 output — ValidationMeasure entity + constraints + state lifecycle
├── quickstart.md        # Phase 1 output — curl recipe: instantiate from catalog → record value → observe status
├── contracts/
│   └── validation-measure-api.openapi.yaml   # OpenAPI 3.0 fragment for 6 endpoints + legacy deprecation header
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
│   │   │   │   ├── ValidationMeasureController.java       # NEW — endpoints under /api/validations/{id}/measures
│   │   │   │   └── ValidationResultController.java        # EXISTING — unchanged code, headers added by filter
│   │   │   ├── service/
│   │   │   │   ├── ValidationMeasureService.java          # NEW — interface
│   │   │   │   ├── ValidationMeasureServiceImpl.java      # NEW — @Transactional; calls deviation calculator + editability guard
│   │   │   │   ├── MeasureDeviationCalculator.java        # NEW — pure component, the only path to status/deviation
│   │   │   │   └── MeasureEditabilityGuard.java           # NEW — checks owning ticket status == EN_COURS
│   │   │   ├── repository/
│   │   │   │   └── ValidationMeasureRepository.java       # NEW — JPQL with LEFT JOIN FETCH on catalog_template, entered_by
│   │   │   ├── entity/
│   │   │   │   └── ValidationMeasure.java                 # NEW — @Entity, @EntityListeners(AuditingEntityListener)
│   │   │   ├── dtos/
│   │   │   │   ├── request/
│   │   │   │   │   ├── CreateMeasureRequest.java          # NEW
│   │   │   │   │   ├── UpdateMeasureRequest.java          # NEW
│   │   │   │   │   └── BatchCreateMeasureRequest.java     # NEW — wraps List<CreateMeasureRequest>
│   │   │   │   └── response/
│   │   │   │       ├── ValidationMeasureResponse.java     # NEW
│   │   │   │       └── BatchMeasureErrorResponse.java     # NEW — per-entry diagnostics for 422 payload
│   │   │   ├── mappers/
│   │   │   │   └── ValidationMeasureMapper.java           # NEW
│   │   │   ├── exception/
│   │   │   │   ├── BatchMeasureValidationException.java   # NEW
│   │   │   │   ├── MeasureNotEditableException.java       # NEW — thrown by MeasureEditabilityGuard
│   │   │   │   └── GlobalExceptionHandler.java            # +mappings for the two above (422, 409)
│   │   │   └── config/
│   │   │       └── LegacyResultsDeprecationFilter.java    # NEW — OncePerRequestFilter adding Deprecation header
│   │   └── resources/
│   │       └── db/migration/
│   │           ├── V2.0__validation_measure.sql           # NEW — DDL: table, indexes, unique constraint, FKs
│   │           ├── V2.1__validation_measure_data_migration.sql  # NEW — copies validation_results → validation_measures
│   │           └── V2.2__validation_results_legacy_marker.sql   # NEW — adds migrated_at audit column (no drop)
│   └── test/
│       ├── java/com/pfe/sageline/
│       │   ├── controller/ValidationMeasureControllerTest.java        # NEW — MockMvc contract tests (6 endpoints)
│       │   ├── service/
│       │   │   ├── ValidationMeasureServiceImplTest.java              # NEW — @SpringBootTest + Testcontainers
│       │   │   ├── MeasureDeviationCalculatorTest.java                # NEW — pure unit (canonical SC-001 fixtures)
│       │   │   └── MeasureEditabilityGuardTest.java                   # NEW — status-aware editability
│       │   ├── repository/ValidationMeasureRepositoryTest.java        # NEW — @DataJpaTest
│       │   ├── migration/LegacyMeasureMigrationIntegrationTest.java   # NEW — seeds legacy rows then runs Flyway; asserts 1:1 migration + status mapping
│       │   └── legacy/LegacyValidationResultDeprecationHeaderTest.java # NEW — asserts Deprecation: true header on every legacy response
│       └── resources/
│           └── (no new fixtures — reuses Phase 001's sagemcom-logs/ as integration anchors)
```

**Structure Decision**: Single Spring Boot project; no module split. New code merges into the existing layered structure (controller → service → repository → entity) per Constitution §Additional Constraints. Two new service components (`MeasureDeviationCalculator`, `MeasureEditabilityGuard`) are split out as small, side-effect-free collaborators so each is unit-testable in isolation against the canonical SC-001 fixtures — same pattern Phase 001 used for its catalog audit listener.

## Complexity Tracking

> Constitution Check passes. No violations. This section intentionally empty.
