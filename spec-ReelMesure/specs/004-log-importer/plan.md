# Implementation Plan: Sagemcom Log Importer (Backend Only)

**Branch**: `004-sagemcom-log-importer` | **Date**: 2026-05-14 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-log-importer/spec.md`

## Summary

Parse Sagemcom production log files uploaded against an `EN_COURS` validation ticket and auto-populate its `ValidationMeasure` rows. Two endpoints — `POST /api/validations/{id}/preview-log` (non-mutating diagnostic) and `POST /api/validations/{id}/import-log` (commit) — are served by the same parsing pipeline so preview and commit produce byte-identical match/unmatched/warning lists (FR-005/FR-006, SC-003). Source-log files are persisted under `storage/logs/{validationId}/` per Constitution V; every imported `ValidationMeasure` row links back to its origin file and exposes a snippet endpoint for audit (FR-008/FR-009). A successful import triggers the Phase 003 readiness recomputation so the existing STOMP topic refreshes the UI without polling (FR-016, SC-001).

Technical approach (resolved by `/speckit-clarify` 2026-05-14 and Phase 0 below):

- **Three parsing strategies, one orchestrator.** `SagemcomLogParser` is a `LogFormatStrategy`-based dispatcher with three concrete strategies (`BnftLogStrategy`, `BwcLogStrategy`, `BtfLogStrategy`) plus a header-sniff step that picks one — or fails with a structured error if none match. Each strategy returns the same `ParsedLog` value object (detected format + list of `ParsedMeasure { code, label, sourceDeclaredStatus, min, max, unit, value? }`). The final-measure-block regex from `Plan.md` §9 is the BNFT/BTF base; BWC adds an inline `POWER_RMS_AVG_VSA1 ... (lower, upper)` secondary scan.
- **Wrong-station soft-block (Clarify Q1).** When the detected format does not match the ticket zone's `PosteType`, the importer does **not** refuse: it returns the preview/import report normally, surfaces a `WRONG_STATION_FORMAT` warning, and persists whatever aliased entries match. Hard-blocking would forbid legitimate cross-station alias entries the catalog admin configured. The warning is sticky — it appears in both preview and commit reports.
- **NOT_EXECUTED-only overwrite by default (Clarify Q2).** Repeat imports upsert by `measureCode`. Rows currently in `NOT_EXECUTED` are replaced; rows already in `OK`/`OUT_OF_RANGE` are reported in a dedicated `wouldOverwrite` section and persist only when the caller sets `overwriteExisting=true` on the commit request. Preview always discloses the count and codes regardless of the flag. Manual-vs-imported origin is **not** used as a gate.
- **Source status recorded separately (Clarify Q3).** Every imported `ValidationMeasure` gains a non-authoritative `sourceDeclaredStatus` column (Sagemcom 0/1/2 as `OK_FROM_LOG` / `OUT_OF_RANGE_FROM_LOG` / `NOT_EXECUTED_FROM_LOG`). The authoritative `status` continues to be recomputed by `MeasureDeviationCalculator` (Constitution III, Phase 002). When the two disagree, the import report carries a `STATUS_DIVERGENCE` warning per measure — surfaces catalog-vs-station bound drift.
- **2 MB upload cap (Clarify Q4).** Enforced at the controller via Spring's `spring.servlet.multipart.max-file-size=2MB` + an explicit defensive guard so the limit also applies if multipart config is changed later. Oversized uploads return HTTP 413 with a clear message — before any disk I/O or parsing.
- **Retention bound to ticket (Clarify Q5).** Files live under `storage/logs/{validationId}/{originalName}` exactly as Constitution V already mandates. No scheduled purge. `ImportedLogFile` rows cascade-delete with their parent `Validation`. The FR-009 "source no longer available" fallback covers the disaster-recovery case only (file missing on disk while the row still exists).
- **Editability guard reused, not duplicated.** Phase 002's `MeasureEditabilityGuard` already enforces "measures are only writable while the ticket is in `EN_COURS`". The importer routes every persistence step through that guard rather than re-implementing the rule (FR-010).
- **Readiness refresh through Phase 003.** After a successful import (and only after — preview does not push), the importer calls `WorkflowReadinessService.publishSnapshot(validationId)` so the existing `/topic/validation.{id}.readiness` topic gets the fresh state (FR-016, Story 1 acceptance).
- **One-at-a-time per ticket.** Enforced with a Postgres advisory lock keyed on `('log-import', validationId)`. A second concurrent attempt returns HTTP 409 with `IMPORT_IN_PROGRESS` rather than racing. Plain `synchronized` is rejected because the deploy is single-instance today but the lock-on-row pattern is correct even when scaled.
- **Atomic commit, atomic rollback.** The persist phase runs inside a single `@Transactional` boundary: every matched + would-overwrite row that the flags select is upserted, the `ImportedLogFile` row is created, and the readiness publish is queued *after* commit via `TransactionSynchronizationManager.registerSynchronization`. A failure mid-write rolls back every measure plus the `ImportedLogFile` row; the on-disk file is written *before* the transaction starts (so it is recoverable for audit even if the DB rolls back) but is deleted by a `@TransactionalEventListener(phase=AFTER_ROLLBACK)` so disk and DB stay consistent on the happy and the sad path.
- **Alias table is config-seeded.** A new `measure_code_alias` table maps `(posteType, sourceCode) → catalogMeasureCode`; entries are seeded via Flyway (`V4.1__measure_code_alias.sql`) with the handful of known equivalences from `Plan.md` §9 (e.g., `MES_BNFT_PWR0_2G ≡ PWR_2G_ANT0`). No admin UI in this phase — the "Add to catalog" inline action in the preview dialog reuses the existing Phase 001 catalog endpoints, not an alias-management surface.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 4.0.2 (web-mvc, data-jpa, validation, security, oauth2-resource-server, websocket / STOMP), `spring-web` multipart, Lombok, PostgreSQL JDBC, Flyway Core + Flyway PostgreSQL (already on the path from Phases 001 / 002 / 003). No new third-party library: regex parsing is plain `java.util.regex`, no Apache Commons CSV / no Antlr.
**Storage**: PostgreSQL 15+ database `sageLine_db` on `localhost:5432`. This phase adds **two tables** (`imported_log_file`, `measure_code_alias`), one column on `validation_measures` (`source_declared_status VARCHAR(32) NULL`), and one FK column on `validation_measures` (`imported_log_file_id BIGINT NULL` referencing `imported_log_file.id`). The original upload bytes live on the local filesystem under `storage/logs/{validationId}/{originalName}` (Constitution V). Snippet retrieval reads from disk; the table holds only metadata + path. A configurable application property `sageline.import.storage-root` defaults to `storage/logs` and is `${user.dir}`-relative for dev / absolute for prod.
**Testing**: JUnit 5, Spring Boot Test (`@SpringBootTest`, `@DataJpaTest`, `MockMvc`), Spring Security Test (`@WithMockUser` / JWT mock), Testcontainers PostgreSQL for integration tests, AssertJ for fluent assertions. Real-log fixtures already exist under `src/test/resources/fixtures/sagemcom-logs/` (`bnft-decoder-M393.txt`, `bwc-gateway-safran-wifi5g.log`, `btf-gateway-fb107-wifi7.log`) — these are the canonical inputs for the strategy unit tests and the lifecycle integration tests (Constitution VII).
**Target Platform**: Linux / Windows JVM — same deployable as the rest of SageLine.
**Project Type**: Web service (Spring Boot monolith); backend-only delivery for this phase (frontend deliverables from `Plan.md` §9 — `LogImportDialog`, source-snippet paperclip, format chip — are deferred and tracked separately; the backend contract is frozen so frontend can begin mid-phase as Constitution "Cross-phase rules" allows).
**Performance Goals**: Preview + import of the three supervisor fixtures (≤ 300 KB each) ≤ 800 ms p95 end-to-end on a cold cache. Snippet retrieval ≤ 100 ms p95. Readiness recomputation after import remains bound by Phase 003's 300 ms p95 envelope. SC-001's 30-second end-to-end demo budget is the user-facing target.
**Constraints**: Constitution V — every imported measure traces back to its source log; the parser MUST persist the file and link every row. Constitution VII (NON-NEGOTIABLE) — the three supervisor fixtures are the integration-test inputs, mocks are only allowed for negative tests (corrupted / unsupported / missing-final-block). Constitution IV — the import must not silently bypass the workflow editability guard; an import on a non-`EN_COURS` ticket returns 409 / 422 with a clear reason. Constitution VI — no JPA entity types in `@RestController` signatures, including the multipart endpoint; the `MultipartFile` argument is consumed in the controller and converted to a typed `LogImportCommand` before reaching the service.
**Scale/Scope**: ~hundreds of tickets per month × ≤ 100 measures × ≤ 3 imports per ticket lifetime. Single-instance deploy; the advisory-lock concurrency story scales horizontally without changes. Disk footprint upper-bound: 100k tickets × 3 logs × 300 KB ≈ 90 GB at end-of-life — well within commodity disk; no time-based purge required per Clarify Q5.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version: **1.0.0** (file: `.specify/memory/constitution.md`).

| # | Principle | Applies? | Status | Notes |
|---|-----------|----------|--------|-------|
| I | Industrial Fidelity | ✅ | PASS | Every name introduced (`ImportedLogFile`, `MeasureCodeAlias`, `sourceDeclaredStatus`, `LogImportReport`) sits in the parsing/audit vocabulary. No new measure-domain term is invented — the parser maps log codes onto the Phase 001 catalog vocabulary verbatim. |
| II | Bounded Tolerance, Not Target | ✅ | PASS | The parser reads `[min, max]` from the log final-block and writes them onto `ValidationMeasure.lowerBound/upperBound`. No `expectedValue` field touched anywhere. |
| III | Three-Valued Measure Status | ✅ | PASS | Authoritative status is recomputed by `MeasureDeviationCalculator` (Phase 002). `sourceDeclaredStatus` is a *separate* non-authoritative column with its own enum and is documented as such in `data-model.md`; it is never used by the workflow guard, the conformity engine, or the KPI service. |
| IV | Guarded Transitions (NON-NEGOTIABLE) | ✅ | PASS | The importer never sets `validation.status` directly. Import is gated to `EN_COURS` by the existing `MeasureEditabilityGuard`; post-import the importer calls `WorkflowReadinessService.publishSnapshot(...)` so the Phase 003 guard sees the new coverage when the user clicks Submit-for-Review. |
| V | Traceability from Log to Verdict | ✅ | PASS | This phase is the materialization of Principle V. Every imported `ValidationMeasure` row carries `imported_log_file_id`; `GET /api/validations/{id}/measures/{measureId}/source-snippet` returns the originating log fragment by reading from `storage/logs/{validationId}/{originalName}`. |
| VI | DTO / Entity Separation | ✅ | PASS | Controller signatures are `LogImportReportDTO` (response) and `MultipartFile` + `LogImportOptionsDTO` (request). Service signatures use a typed `LogImportCommand`. Mapper `LogImportReportMapper` in `mappers/`. No `@Entity` type leaks. |
| VII | Real-Log Test Fixtures (NON-NEGOTIABLE) | ✅ | PASS | The three supervisor fixtures already live under `src/test/resources/fixtures/sagemcom-logs/` (verified). Strategy unit tests and the four integration scenarios in §"Test plan" below feed them through the pipeline. Mocks are restricted to four negative tests (corrupted, unsupported-station, missing-final-block, oversized — the size negative test uses a synthetic 3 MB buffer rather than a real Sagemcom file to avoid committing junk). |
| VIII | Backward Compatibility During Refactor | ➖ | N/A | This phase introduces new endpoints only. No legacy endpoint is touched; the Phase 002 deprecated `/api/validation-results` is not in scope. |
| IX | Auditability of Overrides | ➖ | N/A | Override semantics belong to Phase 005. The `STATUS_DIVERGENCE` warning surfaced by this phase is *audit signal*, not an override; the verdict path is untouched. |
| X | No Premature AI Integration | ✅ | PASS | Pure regex + Java strategies; no LLM, no model invocation, no AI dependency added to `pom.xml`. |
| XI | Frontend Stack Consistency | ➖ | N/A | Backend-only phase. |
| XII | Role-Gated UI (NON-NEGOTIABLE) | ✅ (backend half) | PASS | `@PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")` on the preview, import, and snippet endpoints (FR-011). Cross-line access continues to be enforced by `ValidationService.findById`'s production-line scoping, the same mechanism Phase 003 reuses. |

**Additional Constraints (Constitution §"Additional Constraints"):**

- **Layered structure** — Controller → Service → Repository. Parser strategies live under `service/import/parser/`; the orchestrating `SagemcomLogParser`, the storage adapter, and the importer service live one level up in `service/import/`. Controllers stay thin pass-throughs.
- **Source-log storage** — Constitution V's `storage/logs/{validationId}/{originalName}` is the storage layout, verbatim. The `sageline.import.storage-root` property only chooses the *root* directory; the per-ticket layout is fixed.
- **Repository queries** — `ValidationMeasureRepository.findByValidationIdAndMeasureCodeIn(...)` is added (single JPQL, returns existing rows for the upsert decision); `ImportedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(...)` for snippet retrieval; both use `@Query` JPQL with explicit fetch where relations are rendered. No N+1.
- **Single source of truth for the report** — `LogImportPipeline.run(command, dryRun)` is the only producer of `LogImportReportDTO`. Preview is `run(command, dryRun=true)`, commit is `run(command, dryRun=false)` followed by `persist(report)`. Preview and commit cannot diverge by construction (SC-003).
- **Atomic commit / disk cleanup** — file write happens before `@Transactional`; on `AFTER_ROLLBACK`, a registered listener deletes the orphaned file. On `AFTER_COMMIT` it publishes the STOMP snapshot.

**Result: Constitution Check PASSES.** No deviations to declare; `Complexity Tracking` section omitted.

## Project Structure

### Documentation (this feature)

```text
specs/004-log-importer/
├── plan.md              # This file
├── spec.md              # Feature spec (with Clarifications session 2026-05-14, 5 Qs)
├── research.md          # Phase 0 output — parser strategy design, advisory-lock concurrency, source-status divergence semantics, storage layout, alias table
├── data-model.md        # Phase 1 output — ImportedLogFile, MeasureCodeAlias, ValidationMeasure extension, DTO shapes
├── quickstart.md        # Phase 1 output — curl recipe: preview → confirm → re-import with overwriteExisting → fetch snippet
├── contracts/
│   └── log-importer-api.openapi.yaml   # OpenAPI 3.0 fragment for preview, import, snippet; LogImportReportDTO schema; multipart requests
├── checklists/
│   └── requirements.md  # Spec-quality checklist (created by /speckit-specify)
└── tasks.md             # Phase 2 output — created by /speckit-tasks (not yet)
```

### Source Code (repository root)

The existing Spring Boot monolith. New files land under `com.pfe.sageline.*` and `src/main/resources/db/migration/`. No new modules. The package `service/import/` is new (mirrors the `service/workflow/` precedent from Phase 003).

```text
sageLine-backend/
├── src/
│   ├── main/
│   │   ├── java/com/pfe/sageline/
│   │   │   ├── controller/
│   │   │   │   ├── LogImportController.java                    # NEW — POST /api/validations/{id}/preview-log, POST /api/validations/{id}/import-log (multipart)
│   │   │   │   └── MeasureSourceController.java                # NEW — GET /api/validations/{id}/measures/{measureId}/source-snippet (FR-009)
│   │   │   ├── service/
│   │   │   │   ├── import/
│   │   │   │   │   ├── LogImportService.java                   # NEW — public surface: preview(...) and importLog(...) returning LogImportReportDTO
│   │   │   │   │   ├── LogImportPipeline.java                  # NEW — orchestrates: sniff → parse → match → classify → (persist if !dryRun)
│   │   │   │   │   ├── LogStorageService.java                  # NEW — writes/reads under storage/logs/{validationId}/{originalName}; snippet extraction
│   │   │   │   │   ├── MeasureMatcher.java                     # NEW — catalog lookup + alias fallback per (posteType, code) → matched/unmatched buckets
│   │   │   │   │   ├── ImportLockService.java                  # NEW — Postgres advisory lock keyed on ('log-import', validationId); returns Closeable
│   │   │   │   │   ├── SourceStatusReconciler.java             # NEW — compares Sagemcom 0/1/2 → recomputed MeasureStatus → emits STATUS_DIVERGENCE warnings
│   │   │   │   │   └── parser/
│   │   │   │   │       ├── LogFormatStrategy.java              # NEW — interface: supports(headerSample) → boolean; parse(content) → ParsedLog
│   │   │   │   │       ├── HeaderSniffer.java                  # NEW — reads first N bytes, picks the strategy or throws UnsupportedLogFormatException
│   │   │   │   │       ├── BnftLogStrategy.java                # NEW — header `EZR-AVS*` → final-block regex
│   │   │   │   │       ├── BwcLogStrategy.java                 # NEW — header `EZR-BBS27* + BWC` → final-block + inline POWER_RMS_AVG_VSA1 secondary scan
│   │   │   │   │       ├── BtfLogStrategy.java                 # NEW — header `EZR-BBS22* + BTF` → final-block regex
│   │   │   │   │       └── ParsedLog.java                      # NEW — record(format, List<ParsedMeasure>, List<String> parserNotes)
│   │   │   │   ├── MeasureEditabilityGuard.java                # EXISTING — invoked by LogImportPipeline before any persistence (Phase 002)
│   │   │   │   ├── MeasureDeviationCalculator.java             # EXISTING — recomputes authoritative status + deviationPct (Phase 002)
│   │   │   │   ├── ValidationMeasureServiceImpl.java           # EXISTING — bulk upsert path reused for the import-commit step; readiness snapshot publish hook from Phase 003 fires naturally on each upsert
│   │   │   │   └── workflow/WorkflowReadinessService.java      # EXISTING — Phase 003: importer calls publishSnapshot(validationId) after AFTER_COMMIT
│   │   │   ├── repository/
│   │   │   │   ├── ImportedLogFileRepository.java              # NEW — find by validation, by measure FK
│   │   │   │   ├── MeasureCodeAliasRepository.java             # NEW — find by (posteType, sourceCode)
│   │   │   │   └── ValidationMeasureRepository.java            # EXISTING — adds findByValidationIdAndMeasureCodeIn(...) for upsert lookup
│   │   │   ├── entity/
│   │   │   │   ├── ImportedLogFile.java                        # NEW — id, validation FK, originalName, detectedFormat, storedPath, sizeBytes, uploadedBy, uploadedAt
│   │   │   │   ├── MeasureCodeAlias.java                       # NEW — id, posteType, sourceCode, catalogMeasureCode, active
│   │   │   │   └── ValidationMeasure.java                      # EXISTING — adds `@ManyToOne ImportedLogFile importedLogFile` + `SourceDeclaredStatus sourceDeclaredStatus`
│   │   │   ├── enums/
│   │   │   │   └── SourceDeclaredStatus.java                   # NEW — OK_FROM_LOG, OUT_OF_RANGE_FROM_LOG, NOT_EXECUTED_FROM_LOG (mapping 0/1/2)
│   │   │   ├── dtos/
│   │   │   │   ├── request/
│   │   │   │   │   └── LogImportOptionsDTO.java                # NEW — { overwriteExisting: boolean }; bound from multipart form field
│   │   │   │   └── response/
│   │   │   │       ├── LogImportReportDTO.java                 # NEW — detectedFormat, totalParsed, matched[], unmatched[], wouldOverwrite[], warnings[]
│   │   │   │       ├── MatchedMeasureDTO.java                  # NEW — measureCode, value, computedStatus, sourceDeclaredStatus, templateId
│   │   │   │       ├── UnmatchedMeasureDTO.java                # NEW — measureCode, value?, reason
│   │   │   │       ├── WouldOverwriteMeasureDTO.java           # NEW — measureCode, currentValue, currentStatus, newValue, newComputedStatus
│   │   │   │       └── SourceSnippetDTO.java                   # NEW — originalFilename, snippet, available (boolean; false when file missing on disk)
│   │   │   ├── mappers/
│   │   │   │   └── LogImportReportMapper.java                  # NEW — ParsedLog + match results → LogImportReportDTO
│   │   │   ├── exception/
│   │   │   │   ├── UnsupportedLogFormatException.java          # NEW → 422 with detected sniff sample
│   │   │   │   ├── LogParseException.java                      # NEW → 422 with parser note list
│   │   │   │   ├── LogTooLargeException.java                   # NEW → 413
│   │   │   │   ├── ImportInProgressException.java              # NEW → 409
│   │   │   │   └── GlobalExceptionHandler.java                 # EXISTING — +mappings for the four exceptions above
│   │   │   └── Config/
│   │   │       └── LogImportProperties.java                    # NEW — @ConfigurationProperties("sageline.import"): storageRoot, maxFileSize (default 2MB), snippetLines (default 12)
│   │   └── resources/
│   │       ├── application.properties                          # EXISTING — adds spring.servlet.multipart.max-file-size=2MB, sageline.import.storage-root=storage/logs
│   │       └── db/migration/
│   │           ├── V4.0__imported_log_file.sql                 # NEW — imported_log_file table + validation_measures.imported_log_file_id FK + source_declared_status column
│   │           └── V4.1__measure_code_alias.sql                # NEW — measure_code_alias table + seed rows for known equivalences from Plan.md §9
│   └── test/
│       ├── java/com/pfe/sageline/
│       │   ├── controller/
│       │   │   ├── LogImportControllerTest.java                # NEW — MockMvc: 200 preview, 200 import, 422 unsupported, 413 oversize, 409 in-progress, 403 wrong role
│       │   │   └── MeasureSourceControllerTest.java            # NEW — MockMvc: 200 snippet, 200 with available=false when file missing, 404 wrong ids, 403 wrong role
│       │   ├── service/import/
│       │   │   ├── parser/
│       │   │   │   ├── BnftLogStrategyTest.java                # NEW — fixture: bnft-decoder-M393.txt → ≥6 ParsedMeasure with expected codes/bounds
│       │   │   │   ├── BwcLogStrategyTest.java                 # NEW — fixture: bwc-gateway-safran-wifi5g.log → ≥16 ParsedMeasure incl. POWER_RMS_AVG_VSA1 inline scan
│       │   │   │   ├── BtfLogStrategyTest.java                 # NEW — fixture: btf-gateway-fb107-wifi7.log → ≥14 ParsedMeasure across FXS/voice
│       │   │   │   ├── HeaderSnifferTest.java                  # NEW — picks correct strategy per fixture; rejects unknown header
│       │   │   │   └── NegativeParseTest.java                  # NEW — corrupted bytes, missing final block, truncated mid-final-block (synthetic content; Constitution VII exception for negatives)
│       │   │   ├── MeasureMatcherTest.java                     # NEW — catalog hit, alias hit, unmatched-with-reason
│       │   │   ├── SourceStatusReconcilerTest.java             # NEW — agree-no-warning, disagree-emits-STATUS_DIVERGENCE-warning
│       │   │   └── LogImportPipelineTest.java                  # NEW — preview ≡ commit (SC-003); overwriteExisting toggle behavior; wrong-station soft-block warning surfaces
│       │   ├── integration/
│       │   │   ├── BnftImportLifecycleIT.java                  # NEW — fixture → preview → import → readiness 6/6 (or whatever TEST_FONCTIONNEL mandates) → STOMP snapshot fires
│       │   │   ├── BwcImportLifecycleIT.java                   # NEW — fixture → preview → import → readiness ≥16/16 → snippet retrievable for each imported measure
│       │   │   ├── BtfImportLifecycleIT.java                   # NEW — fixture → preview → import → readiness goal met (SC-002)
│       │   │   ├── ReimportOverwriteIT.java                    # NEW — first import → manual edit of one row → second import without flag (row preserved) → with flag (row replaced); SC-006
│       │   │   ├── ConcurrentImportIT.java                     # NEW — two parallel calls → exactly one succeeds, one 409; SC-008
│       │   │   ├── EditabilityGuardIT.java                     # NEW — import attempted on PLANIFIE / EN_REVUE → 409 (consistent with Phase 002 guard); no measure created
│       │   │   ├── WrongStationSoftBlockIT.java                # NEW — BNFT log dropped on WIFI_CONDUIT ticket → report carries WRONG_STATION_FORMAT, alias matches still persist
│       │   │   └── DisasterRecoveryIT.java                     # NEW — delete on-disk file after import → snippet endpoint returns 200 with available=false; FR-009 fallback
│       │   └── migration/
│       │       └── ImportedLogFileMigrationTest.java            # NEW — runs V4.0/V4.1; asserts columns + FK + seeded alias rows
│       └── resources/
│           └── fixtures/sagemcom-logs/                          # EXISTING — already contains the three supervisor logs (verified on disk)
```

**Structure Decision**: Single Spring Boot project; no module split. The new `service/import/` sub-package and its `parser/` sub-sub-package mirror Phase 003's `service/workflow/` precedent so the strategy classes can be browsed as a unit and new station strategies can be added without touching the orchestrator. The pipeline + matcher + storage + lock + reconciler are split into separate small classes specifically so each is unit-testable in isolation against the canonical fixtures — the same decomposition pattern Phases 001/002/003 used.

## Complexity Tracking

> Constitution Check passes. No violations. This section intentionally empty.
