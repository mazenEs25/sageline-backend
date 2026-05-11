# Implementation Plan: PosteType Catalog (Backend Only)

**Branch**: `001-poste-type-catalog` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-poste-type-catalog/spec.md`

## Summary

Introduce a curated, role-gated reference catalog of measure templates (`PosteMeasureCatalog`) for each value of the existing `PosteType` enum, exposed via REST endpoints and persisted in PostgreSQL. The catalog feeds every later refactor phase: workflow guard (003), log importer (004), conformity engine (005), KPI rollups (006).

Technical approach (resolved during `/speckit-clarify` and Phase 0 below):
- New JPA entity + repository + service + controller + DTO/mapper layer under the existing `com.pfe.sageline.*` packages.
- **Flyway** introduced as the schema-migration mechanism (`V1.0__baseline.sql`, `V1.1__poste_catalog.sql`, `V1.2__seed_poste_catalog.sql`); Hibernate flips to `ddl-auto=validate`.
- Spring Data JPA auditing wired to `SecurityUtils.getCurrentUserId()` for `createdBy`/`updatedBy`; `@CreatedDate`/`@LastModifiedDate` for timestamps.
- Soft delete via `active` boolean, surfaced through an `includeInactive` query parameter.
- Atomic batch endpoint via a `@Transactional` service method with all-or-nothing validation.
- Seed sourced from the three supervisor logs; rows without explicit `[min, max]` bounds enter as `active=false` with inline SQL comments naming the source log.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 4.0.2 (web-mvc, data-jpa, validation, security, oauth2-resource-server), Lombok, PostgreSQL JDBC, **Flyway Core + Flyway PostgreSQL** (new dependencies added in this phase)
**Storage**: PostgreSQL 15+ database `sageLine_db` on `localhost:5432`
**Testing**: JUnit 5, Spring Boot Test (`@SpringBootTest`, `@DataJpaTest`, `MockMvc`), Spring Security Test (`@WithMockUser` / JWT mock), Testcontainers PostgreSQL (for integration tests against a real DB)
**Target Platform**: Linux/Windows server (JVM) — same deployable as the rest of SageLine
**Project Type**: Web service (Spring Boot monolith); backend-only delivery for this phase
**Performance Goals**: Read of one poste's catalog ≤ 200 ms p95 with ≤ 1 000 catalog rows (SC-002). Single mutating call ≤ 100 ms p95.
**Constraints**: No frontend deliverable in this phase. Flyway must take over the existing schema via a baseline migration without dropping data. JPA entities MUST NOT appear in REST responses (Constitution Principle VI).
**Scale/Scope**: ~22 poste types × ~10–20 measures each ⇒ ≤ 500 active rows expected steady-state; ≤ 1 000 including soft-deleted history. Single-instance deploy; no horizontal scaling concern for this phase.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version: **1.0.0** (file: `.specify/memory/constitution.md`).

| # | Principle | Applies? | Status | Notes |
|---|-----------|----------|--------|-------|
| I | Industrial Fidelity | ✅ | PASS | Seed codes (`MES_*`, `POWER_*`, `M_*`) sourced verbatim from the three supervisor logs; no invented terms. |
| II | Bounded Tolerance, Not Target | ✅ | PASS | Entity carries `defaultLowerBound` + `defaultUpperBound`; no `expectedValue` column introduced. |
| III | Three-Valued Measure Status | ✅ | PASS | `MeasureStatus` enum (`OK`/`OUT_OF_RANGE`/`NOT_EXECUTED`) added in this phase as a shared type; persisted on `ValidationMeasure` in Phase 002, not on the catalog. |
| IV | Guarded Transitions | ➖ | N/A | This phase does not introduce ticket transitions. Catalog publishes the data Phase 003 will guard on. |
| V | Traceability from Log to Verdict | ➖ | DEFERRED | Catalog itself has no log linkage; `sourceLogFile` lives on `ValidationMeasure` (Phase 002). Seed migration comments name source logs (audit-visible). |
| VI | DTO / Entity Separation | ✅ | PASS | Controller signatures use `PosteMeasureCatalogRequest` / `PosteMeasureCatalogResponse`; mapper in `mappers/PosteMeasureCatalogMapper`. |
| VII | Real-Log Test Fixtures | ✅ | PASS | Seed migration is derived from the three supervisor logs; an integration test asserts seeded counts and a sample of measure codes against `bnft-decoder-M393.txt`, `bwc-gateway-safran-wifi5g.log`, `btf-gateway-fb107-wifi7.log` once they are committed under `src/test/resources/fixtures/sagemcom-logs/` (committed as part of this phase, consumed in full by Phase 004). |
| VIII | Backward Compatibility During Refactor | ➖ | N/A | This phase introduces new tables/endpoints; no legacy artifact is being deprecated. |
| IX | Auditability of Overrides | ➖ | N/A | Override concept applies to conformity verdicts (Phase 005). However, the audit quartet on the catalog (FR-023) is in the same audit spirit. |
| X | No Premature AI Integration | ✅ | PASS | No AI imports added. |
| XI | Frontend Stack Consistency | ➖ | N/A | Backend-only phase. |
| XII | Role-Gated UI | ✅ (backend half) | PASS | `@PreAuthorize` on every mutating endpoint per FR-010..013; reads require authentication (FR-016, FR-017). |

**Additional Constraints (Constitution §"Additional Constraints"):**
- DB constraint on `(poste_type, measure_code)` uniqueness ⇒ enforced via partial unique index on active rows (see `V1.1__poste_catalog.sql`).
- `MeasureDeviationCalculator` ⇒ not in scope for this phase (Phase 002).
- JPQL with `LEFT JOIN FETCH` ⇒ not applicable to this phase (catalog entity has no eager relations).

**Result: Constitution Check PASSES.** No deviations to declare; `Complexity Tracking` section omitted.

## Project Structure

### Documentation (this feature)

```text
specs/001-poste-type-catalog/
├── plan.md              # This file
├── spec.md              # Feature specification (with Clarifications session 2026-05-11)
├── research.md          # Phase 0 output — Flyway adoption, JPA auditing, partial unique index
├── data-model.md        # Phase 1 output — PosteMeasureCatalog entity + enums
├── quickstart.md        # Phase 1 output — curl recipe to add and read a template
├── contracts/
│   └── poste-catalog-api.openapi.yaml   # OpenAPI 3.0 fragment for all 7 endpoints
├── checklists/
│   └── requirements.md  # Spec-quality checklist (created by /speckit-specify)
└── tasks.md             # Phase 2 output — created by /speckit-tasks (not yet)
```

### Source Code (repository root)

The existing Spring Boot monolith. New files added by this phase live under
`com.pfe.sageline.*` and `src/main/resources/db/migration/`. No new modules, no new
top-level directories.

```text
sageLine-backend/
├── pom.xml                                       # +flyway-core, +flyway-database-postgresql
├── src/
│   ├── main/
│   │   ├── java/com/pfe/sageline/
│   │   │   ├── SageLineApplication.java          # +@EnableJpaAuditing
│   │   │   ├── Config/
│   │   │   │   └── JpaAuditingConfig.java        # NEW — AuditorAware<Long> bean
│   │   │   ├── controller/
│   │   │   │   └── PosteCatalogController.java   # NEW — 7 endpoints under /api/poste-catalog
│   │   │   ├── service/
│   │   │   │   ├── PosteCatalogService.java      # NEW — interface
│   │   │   │   └── PosteCatalogServiceImpl.java  # NEW — @Transactional
│   │   │   ├── repository/
│   │   │   │   └── PosteMeasureCatalogRepository.java   # NEW — extends JpaRepository
│   │   │   ├── entity/
│   │   │   │   └── PosteMeasureCatalog.java      # NEW — @Entity, @EntityListeners(AuditingEntityListener)
│   │   │   ├── enums/
│   │   │   │   ├── MeasureCategory.java          # NEW
│   │   │   │   └── MeasureStatus.java            # NEW (consumed by Phase 002)
│   │   │   ├── dtos/
│   │   │   │   ├── request/
│   │   │   │   │   ├── PosteMeasureCatalogRequest.java       # NEW
│   │   │   │   │   ├── PosteMeasureCatalogUpdateRequest.java # NEW
│   │   │   │   │   └── PosteMeasureCatalogBatchRequest.java  # NEW
│   │   │   │   └── response/
│   │   │   │       └── PosteMeasureCatalogResponse.java      # NEW
│   │   │   ├── mappers/
│   │   │   │   └── PosteMeasureCatalogMapper.java            # NEW
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java   # +handler for DuplicateCatalogTemplateException
│   │   └── resources/
│   │       ├── application.properties            # ddl-auto: update → validate
│   │       └── db/migration/
│   │           ├── V1.0__baseline.sql            # NEW — baseline of existing schema
│   │           ├── V1.1__poste_catalog.sql       # NEW — DDL
│   │           └── V1.2__seed_poste_catalog.sql  # NEW — 36+ seeded rows
│   └── test/
│       ├── java/com/pfe/sageline/
│       │   ├── controller/PosteCatalogControllerTest.java     # NEW — MockMvc contract tests
│       │   ├── service/PosteCatalogServiceImplTest.java       # NEW — unit + Testcontainers
│       │   ├── repository/PosteMeasureCatalogRepositoryTest.java # NEW — @DataJpaTest
│       │   └── seed/SeedCatalogIntegrationTest.java           # NEW — asserts seeded counts
│       └── resources/
│           ├── application-test.properties                    # NEW — testcontainers DB URL
│           └── fixtures/sagemcom-logs/                        # NEW — 3 supervisor log fixtures
│               ├── bnft-decoder-M393.txt
│               ├── bwc-gateway-safran-wifi5g.log
│               └── btf-gateway-fb107-wifi7.log
```

**Structure Decision**: Single Spring Boot project; no module split. New code merges into the existing layered structure (controller → service → repository → entity) per Constitution §Additional Constraints. The only structural change is the introduction of `src/main/resources/db/migration/` as the Flyway home (the directory already exists, empty).

## Complexity Tracking

> Constitution Check passes. No violations. This section intentionally empty.
