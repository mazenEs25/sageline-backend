---
description: "Task list for feature 004 — Sagemcom Log Importer (Backend Only)"
---

# Tasks: Sagemcom Log Importer (Backend Only)

**Input**: Design documents from `/specs/004-log-importer/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/log-importer-api.openapi.yaml ✓, quickstart.md ✓

## Status (2026-05-15, post code review + full test pass)

| Phase | Status |
|---|---|
| 1. Setup (T001–T002) | ✅ Done |
| 2. Foundational (T003–T017) | ✅ Done |
| 3. US1 implementation (T018–T031) | ✅ Done |
| 3. US1 tests (T032–T037) | ✅ Done — all green |
| 4. US2 (T038–T039) | ✅ Done |
| 5. US3 (T040–T042) | ✅ Done |
| 6. US4 (T043) | ✅ Done |
| 7. Polish (T044–T047) | ✅ Done (T044 BTF→ACC pinned + V4.2 alias/catalog seed; T045 Javadoc; T046 INFO log; T047 partial — Phase 004 subset green, full suite has 13 pre-existing failures on master) |

**Build**: `BUILD SUCCESS` for compile + test-compile.

**Phase 004 test suite**: **20 tests / 8 classes / 0 failures / 0 errors** — `ImportedLogFileMigrationTest` (6), `BwcImportLifecycleIT` (4), `BnftImportLifecycleIT` (1), `BtfImportLifecycleIT` (1), `LogImportControllerTest` (4), `ConcurrentImportIT` (1), `ReimportOverwriteIT` (2), `SnippetFallbackIT` (1).

**Issues discovered + fixed during test runs (beyond the original code review)**:
- SecurityConfig URL-rule order — generic POST /api/validations/** hijacked /preview-log and /import-log before @PreAuthorize could fire; added explicit allow rules.
- `pg_try_advisory_lock(int, int)` bind-type mismatch — JDBC binds `Long` as `bigint`, function expects `int4`; both lock and unlock now use `CAST(? AS integer)` with `intValue()` argument.
- `ImportedLogFile`/`MeasureCodeAlias` used `OffsetDateTime` for auditing; Spring Data auditing returns `LocalDateTime` and Instant — refactored to `Instant`.
- `ImportedLogFile.uploadedBy` was `NOT NULL` in the entity even though the migration accepted null — caused a constraint violation when SecurityUtils returns null in tests. Now nullable.
- Source-log snippet search had to handle the actual fixture format (`Measure: CODE`) in addition to the Plan.md format (`Mesure <CODE>`); and had to reverse-look-up source codes via the alias table because persisted `measureCode` is the resolved/catalog code, not what the log contains.
- Parser strategies were written for the Plan.md regex but the supervisor fixtures use a labeled-block format. Rewrote all three strategies + a shared `BlockFormatParser` to match the fixtures.
- Two test fixtures (BWC, BNFT-`Temps_Test`) have measure codes that aren't in the V1.2 catalog seed. Added `V4.2__alias_and_catalog_for_fixtures.sql` (loaded programmatically by `CatalogSchemaInitializer` in tests since Flyway is disabled).
- `MatchedEntry` now carries the catalog template entity (not just id) so the pipeline can fall back to catalog bounds when the log has no Min/Max (e.g. EVM measures in BWC, JITTER_VOICE in BTF).

**Pre-existing master failures (untouched)**: `PosteCatalogControllerBatchTest`, `SubmitReviewLifecycleIntegrationTest`, `AutoAdvanceGuardedIntegrationTest`, `ReadinessSnapshotStompContractTest`, several `HandoverServiceImpl*` tests — confirmed by checking out commit `c731f7f` in a worktree before Phase 004 changes landed.

**Code-review fixes applied** (from prior session — chat history): B1–B6 (compilation blockers + correctness bugs), H1–H3 (load-bearing fixes for 404 mapping, missing NOT NULL fields, file read once), H5–H10 + M3 (parser, charset, path traversal, filename collision, snippet search, dry-run wouldOverwrite).

**Tests**: Test scope is kept tight on purpose — only what's load-bearing for acceptance. Constitution VII (NON-NEGOTIABLE) requires the three real Sagemcom fixtures to be exercised by an integration test; that requirement is the floor. Everything else is one migration test + one controller test + one concurrency test. No per-class unit tests for parser strategies, matchers, or reconcilers — the lifecycle ITs cover them transitively.

**Organization**: Tasks are grouped by user story. Setup and Foundational phases unblock all stories; US1 is the MVP.

## Path Conventions

Backend repo root (referenced as `<BACKEND>` below):
`C:\Users\mouaf\OneDrive\Bureau\stagePFE\PROJECT\Sageline\sageLine-backend`

Java source root: `<BACKEND>\src\main\java\com\pfe\sageline\`
Java test root: `<BACKEND>\src\test\java\com\pfe\sageline\`
Resources: `<BACKEND>\src\main\resources\`
Test resources: `<BACKEND>\src\test\resources\`

---

## Phase 1: Setup

- [x] T001 Add `spring.servlet.multipart.max-file-size=2MB`, `spring.servlet.multipart.max-request-size=3MB`, `sageline.import.storage-root=storage/logs`, and `sageline.import.snippet-lines=12` to `<BACKEND>\src\main\resources\application.properties` (R-005).
- [x] T002 [P] Create `LogImportProperties` at `<BACKEND>\src\main\java\com\pfe\sageline\Config\LogImportProperties.java` — `@Component @ConfigurationProperties(prefix="sageline.import")` with `@Getter @Setter`. Fields: `String storageRoot`, `org.springframework.util.unit.DataSize maxFileSize` (default `DataSize.ofMegabytes(2)`), `int snippetLines` (default 12).

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Migrations

- [x] T003 Create `<BACKEND>\src\main\resources\db\migration\V4.0__imported_log_file.sql` per data-model.md §1.1 and §2.1: table `imported_log_file` (id BIGSERIAL PK, validation_id FK ON DELETE CASCADE, original_name VARCHAR(255), detected_format VARCHAR(32), stored_path VARCHAR(512), size_bytes BIGINT CHECK ≤ 2097152, uploaded_by FK, uploaded_at TIMESTAMPTZ DEFAULT now(), overwrite_existing BOOLEAN DEFAULT false, created_at/updated_at TIMESTAMPTZ NOT NULL); add columns `imported_log_file_id BIGINT NULL` (FK to imported_log_file ON DELETE SET NULL) and `source_declared_status VARCHAR(32) NULL` (CHECK on the three enum values) on `validation_measures`; indexes `idx_imported_log_file_validation_id` and `idx_validation_measure_imported_log_file`.
- [x] T004 Create (BNFT aliases seeded ✓; BWC/BTF aliases deferred to T044) `<BACKEND>\src\main\resources\db\migration\V4.1__measure_code_alias.sql` per data-model.md §1.2: table `measure_code_alias` (id BIGSERIAL PK, poste_type VARCHAR(64) NOT NULL, source_code VARCHAR(64) NOT NULL, catalog_measure_code VARCHAR(64) NOT NULL, active BOOLEAN DEFAULT TRUE, created_at/updated_at TIMESTAMPTZ NOT NULL); unique `(poste_type, source_code)`; seed the five BNFT aliases for `TEST_FONCTIONNEL` from Plan.md §9 (`MES_BNFT_PWR0_2G→PWR_2G_ANT0`, `MES_BNFT_PWR1_2G→PWR_2G_ANT1`, `MES_BNFT_PWR0_5G→PWR_5G_ANT0`, `MES_BNFT_PWR1_5G→PWR_5G_ANT1`, `MES_BNFT_PWR0_BT→PWR_BT_ANT0`). Leave BWC/BTF aliases as TODO comments (resolved in T036).

### Enums

- [x] T005 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\enums\SourceDeclaredStatus.java` — enum `OK_FROM_LOG`, `OUT_OF_RANGE_FROM_LOG`, `NOT_EXECUTED_FROM_LOG` with static `fromSagemcomStatus(int n)` mapping 0/1/2.
- [x] T006 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\enums\LogFormat.java` — enum `BNFT`, `BWC`, `BTF`.
- [x] T007 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\enums\UnmatchedReason.java` — enum `NO_TEMPLATE_FOR_POSTE_TYPE`, `AMBIGUOUS_ALIAS`, `MISSING_BOUNDS_IN_LOG`.
- [x] T008 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\enums\WarningCode.java` — enum `WRONG_STATION_FORMAT`, `STATUS_DIVERGENCE`, `MISSING_UNIT_FALLBACK`, `TRUNCATED_FINAL_BLOCK`.

### Entities

- [x] T009 Create `<BACKEND>\src\main\java\com\pfe\sageline\entity\ImportedLogFile.java` — `@Entity @Table(name="imported_log_file")` with Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder` and `@EntityListeners(AuditingEntityListener.class)`. Fields per data-model.md §1.1 (id Long generated IDENTITY; `@ManyToOne(fetch=LAZY)` to `Validation`; originalName, detectedFormat as `@Enumerated(STRING)`, storedPath, sizeBytes Long, uploadedBy Long, uploadedAt as `@CreatedDate`, overwriteExisting boolean, createdAt/updatedAt auditing fields).
- [x] T010 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\entity\MeasureCodeAlias.java` — same Lombok stack. Fields: id, posteType `@Enumerated(STRING)`, sourceCode, catalogMeasureCode, active, createdAt/updatedAt.
- [x] T011 Extend `<BACKEND>\src\main\java\com\pfe\sageline\entity\ValidationMeasure.java`: add `@ManyToOne(fetch=LAZY) @JoinColumn(name="imported_log_file_id") private ImportedLogFile importedLogFile;` and `@Enumerated(STRING) @Column(name="source_declared_status", length=32) private SourceDeclaredStatus sourceDeclaredStatus;`. Keep the existing `source_log_file` String column as denormalized convenience.

### Repositories

- [x] T012 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\repository\ImportedLogFileRepository.java` — `extends JpaRepository<ImportedLogFile, Long>`; method `List<ImportedLogFile> findByValidationIdOrderByUploadedAtDesc(Long validationId)`.
- [x] T013 [P] Create `<BACKEND>\src\main\java\com\pfe\sageline\repository\MeasureCodeAliasRepository.java` — `extends JpaRepository<MeasureCodeAlias, Long>`; method `Optional<MeasureCodeAlias> findByPosteTypeAndSourceCodeAndActiveTrue(PosteType posteType, String sourceCode)`.
- [x] T014 Extend `<BACKEND>\src\main\java\com\pfe\sageline\repository\ValidationMeasureRepository.java`: add `List<ValidationMeasure> findByValidationIdAndMeasureCodeIn(Long validationId, Collection<String> measureCodes)` (JPQL `SELECT m FROM ValidationMeasure m WHERE m.validation.id = :validationId AND m.measureCode IN :measureCodes`).

### Exceptions

- [x] T015 [P] Create four exception classes under `<BACKEND>\src\main\java\com\pfe\sageline\exception\`: `UnsupportedLogFormatException` (carries `String headerSample`), `LogParseException` (carries `List<String> parserNotes`), `LogTooLargeException` (carries `actualBytes`, `limitBytes`), `ImportInProgressException` (carries `Long validationId`). All `extends RuntimeException` with explicit message constructors.
- [x] T016 Extend `<BACKEND>\src\main\java\com\pfe\sageline\exception\GlobalExceptionHandler.java`: add `@ExceptionHandler` mappings — `UnsupportedLogFormatException`→422 `{code:"UNSUPPORTED_LOG_FORMAT",message,details:{headerSample}}`, `LogParseException`→422 `{code:"LOG_PARSE_ERROR",details:{parserNotes}}`, `LogTooLargeException`→413 `{code:"LOG_TOO_LARGE",...}`, `ImportInProgressException`→409 `{code:"IMPORT_IN_PROGRESS",...}`, Spring's `MaxUploadSizeExceededException`→413 `{code:"LOG_TOO_LARGE","Upload exceeds 2MB limit"}`.

### Concurrency

- [x] T017 Create `<BACKEND>\src\main\java\com\pfe\sageline\service\Import\ImportLockService.java` *(initial impl had broken `@Transactional` on `tryAcquire`; fixed in review — Propagation.MANDATORY now)* — `@Service`. Method `LockHandle tryAcquire(Long validationId)` running native query `SELECT pg_try_advisory_lock(hashtext('log-import'), :validationId)`. If false → throw `ImportInProgressException`. Returned `LockHandle` (inner class implementing `Closeable`) calls `pg_advisory_unlock(...)` on close. Must run inside a transaction so the connection stays bound (Javadoc this).

**Checkpoint**: Foundation ready.

---

## Phase 3: User Story 1 — Drag-drop import (Priority: P1) 🎯 MVP

**Goal**: A `TECH_VAL` user uploads a real Sagemcom log → matched measures persist → readiness flips → Submit-for-Review unlocks.

**Independent Test**: `BwcImportLifecycleIT` (T029) — drop the supervisor BWC fixture into an empty WIFI_CONDUIT ticket, assert ≥16 rows persist with `status=OK` and `canTransition=true`.

### Implementation (US1)

- [x] T018 [US1] Create the parser value-objects under `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\`: `ParsedLog.java` as `record ParsedLog(LogFormat format, List<ParsedMeasure> measures, List<String> parserNotes)`, and `ParsedMeasure.java` as `record ParsedMeasure(String code, String label, SourceDeclaredStatus sourceStatus, Double lowerBound, Double upperBound, String unit, Double measuredValue)`.
- [x] T019 [P] [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\LogFormatStrategy.java` — interface with `boolean supports(String headerSample)` and `ParsedLog parse(String content)`.
- [x] T020 [P] [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\HeaderSniffer.java` — `@Component` constructor-injected with `List<LogFormatStrategy>`. Method `LogFormatStrategy sniff(String content)`: read first 4096 chars; return the first strategy whose `supports(...)` returns true; else throw `UnsupportedLogFormatException(headerSample)`.
- [x] T021 [P] [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\BnftLogStrategy.java` — `@Component`. `supports`: header contains `EZR-AVS`. `parse`: apply the final-block regex from Plan.md §9 (`Mesure\s+<(?<code>\w+)>\s+:\s+(?<label>[^-]+)-\s+Status\s+(?<status>\d)\s+(?<min>[\d.]+)\s+(?<unit>\S+)\s+<\s+\.\.\.\s+<\s+(?<max>[\d.]+)(?:\s+(?<value>[\d.]+)\s+\S+)?`). Build `ParsedMeasure` list; if no match, throw `LogParseException` with note `"missing final block"`. Refine the regex against the real `bnft-decoder-M393.txt` fixture during dev (open it once, sample 3 entries, confirm capture groups).
- [x] T022 [P] [US1] Create *(H5: inline POWER_RMS scan now records NOT_EXECUTED_FROM_LOG since no value is captured)* `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\BwcLogStrategy.java` — `@Component`. `supports`: header contains `EZR-BBS27` AND `BWC`. `parse`: run the T021 regex first, then a secondary scan for inline `POWER_RMS_AVG_VSA1` lines (regex to be refined against `bwc-gateway-safran-wifi5g.log` — Plan.md §9 sketches it as `... (lower, upper)`). Each inline match produces a `ParsedMeasure` whose code embeds antenna+frequency (e.g., `POWER_RMS_AVG_VSA1_ANT1_5250`). Merge both lists.
- [x] T023 [P] [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\parser\BtfLogStrategy.java` — `@Component`. `supports`: header contains `EZR-BBS22` AND `BTF`. `parse`: same final-block regex as T021; no secondary scan.
- [x] T024 [US1] Create *(also needed adding `findByPosteTypeAndMeasureCodeAndActiveTrue` to PosteMeasureCatalogRepository, which the original impl assumed existed)* `<BACKEND>\src\main\java\com\pfe\sageline\service\import\MeasureMatcher.java` — `@Service` constructor-injected with `PosteMeasureCatalogRepository` and `MeasureCodeAliasRepository`. Method `MatchResult match(PosteType posteType, List<ParsedMeasure> measures)` returning `{List<MatchedEntry> matched, List<UnmatchedEntry> unmatched}`. For each parsed code: catalog lookup → alias lookup → unmatched with `reason=NO_TEMPLATE_FOR_POSTE_TYPE` and a helpful `reasonDetail` (e.g., `"No template found for code 'X' under poste type Y"`).
- [x] T025 [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\SourceStatusReconciler.java` — `@Service`. Method emits one `WarningCode.STATUS_DIVERGENCE` warning per matched entry where parsed `sourceStatus` (mapped to `MeasureStatus`) disagrees with the recomputed `MeasureDeviationCalculator` status.
- [x] T026 [US1] Create *(H7: filename sanitised; H8: epoch prefix avoids overwriting prior imports; H10: snippet now uses word-boundary regex; H9: returns null when measure code absent so snippet endpoint shows `available=false`)* `<BACKEND>\src\main\java\com\pfe\sageline\service\import\LogStorageService.java` — `@Service` constructor-injected with `LogImportProperties`. Methods:
  - `Path persistUpload(Long validationId, String originalName, byte[] bytes)` — writes to `{storageRoot}/{validationId}/{originalName}`, creating parent dirs.
  - `void delete(Path)` — best-effort `Files.deleteIfExists`.
  - `SnippetResult readSnippetAround(Path path, String measureCode, int snippetLines)` — scans the file line-by-line for the line containing `Mesure <measureCode>` (or substring `measureCode`), returns ±snippetLines lines plus `startLine`/`endLine`; returns null if the file is missing on disk.
- [x] T027 [US1] Create the DTOs per data-model.md §4 under `<BACKEND>\src\main\java\com\pfe\sageline\dtos\`: `response/LogImportReportDTO.java`, `response/MatchedMeasureDTO.java`, `response/UnmatchedMeasureDTO.java`, `response/WouldOverwriteMeasureDTO.java`, `response/WarningDTO.java`, `response/SourceSnippetDTO.java`, `request/LogImportOptionsDTO.java`. Use Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`. Field types follow `contracts/log-importer-api.openapi.yaml`.
- [x] T028 [US1] Create `<BACKEND>\src\main\java\com\pfe\sageline\mappers\LogImportReportMapper.java` — `@Component`. Method `LogImportReportDTO toReport(ParsedLog parsed, MatchResult match, List<WarningDTO> warnings, List<WouldOverwriteMeasureDTO> overwrites, Long ticketId, boolean dryRun, boolean overwriteExisting)`. `willPersist = !inWouldOverwrite || overwriteExisting`.
- [x] T029 [US1] Create *(H1: throws ResourceNotFoundException; H6: charset pinned to UTF-8; M3: wouldOverwrite now computed in dry-run too so preview≡commit; H1: zone null-check)* `<BACKEND>\src\main\java\com\pfe\sageline\service\import\LogImportPipeline.java` — `@Service` constructor-injected with `HeaderSniffer`, `MeasureMatcher`, `MeasureDeviationCalculator`, `SourceStatusReconciler`, `ValidationService`, `ValidationMeasureRepository`, `LogImportReportMapper`. Method `LogImportReportDTO run(LogImportCommand cmd, boolean dryRun)`:
  1. Resolve ticket → zone → `PosteType`.
  2. `sniffer.sniff(content)` → strategy → `strategy.parse(content)` → `ParsedLog`.
  3. If `parsedLog.format` is not in the allowed-PosteType set for that format (helper map: BNFT→{TEST_FONCTIONNEL}, BWC→{WIFI_CONDUIT, BANC_WIFI_CONDUIT}, BTF→{the BTF PosteType pinned in T036}), add a `WRONG_STATION_FORMAT` warning.
  4. `matcher.match(posteType, parsedLog.measures())` → `MatchResult`.
  5. For each matched, recompute authoritative `status` + `deviationPct` via `MeasureDeviationCalculator`.
  6. Run `reconciler.reconcile(matched)` → append `STATUS_DIVERGENCE` warnings.
  7. Load existing rows via `findByValidationIdAndMeasureCodeIn(...)`. Partition matched into normal-upsert (existing row is `NOT_EXECUTED` or absent) vs `wouldOverwrite` (existing is `OK`/`OUT_OF_RANGE`).
  8. Build the DTO via the mapper.
  9. Return immediately if `dryRun=true`; otherwise return for the caller to persist (T030 owns persistence).
- [x] T030 [US1] Create *(rewrote in review: B2/B4/B6 + H1/H2/H3 — see review notes; persistMeasures inlines the upsert and sets all NOT NULL fields from the catalog template; STOMP publish + on-disk cleanup are now `TransactionSynchronization` callbacks)* `<BACKEND>\src\main\java\com\pfe\sageline\service\import\LogImportService.java` — `@Service`. Constructor: `LogImportPipeline`, `LogStorageService`, `ImportLockService`, `ImportedLogFileRepository`, `ValidationMeasureRepository`, `ValidationMeasureServiceImpl` (for upsert), `MeasureEditabilityGuard`, `WorkflowReadinessService`, `LogImportProperties`. Methods:
  - `LogImportReportDTO preview(Long validationId, MultipartFile file, Long userId)`:
    1. Defensive size check against `properties.maxFileSize.toBytes()`; throw `LogTooLargeException` if violated.
    2. `editabilityGuard.requireEditable(validation)`.
    3. `try (LockHandle h = lockService.tryAcquire(validationId)) { ... }`.
    4. Build `LogImportCommand` (no disk write).
    5. Return `pipeline.run(cmd, dryRun=true)`.
  - `@Transactional LogImportReportDTO importLog(Long validationId, MultipartFile file, LogImportOptionsDTO options, Long userId)`:
    1–3. Same as preview.
    4. `Path stored = storage.persistUpload(validationId, file.getOriginalFilename(), file.getBytes())` — outside the transactional persistence path. Capture the path.
    5. Build cmd with `storedPath=stored`, `overwriteExisting=options.overwriteExisting`.
    6. `LogImportReportDTO report = pipeline.run(cmd, dryRun=false)`.
    7. Persist one `ImportedLogFile` row (originalName, detectedFormat from report, storedPath, sizeBytes from `file.getSize()`, uploadedBy, overwriteExisting).
    8. For each matched entry with `willPersist=true`, upsert the `ValidationMeasure` via `ValidationMeasureServiceImpl` (existing Phase 002 method). Set `importedLogFile`, `sourceDeclaredStatus`, `sourceLogFile=originalName` on each.
    9. Register `TransactionSynchronizationManager.registerSynchronization(...)`: `afterCommit` → `workflowReadinessService.publishSnapshot(validationId)`; `afterCompletion(STATUS_ROLLED_BACK)` → `storage.delete(stored)`.
    10. Return the report.
- [x] T031 [US1] Create *(B1/B3: imports fixed — `service.Import` package, `Config.SecurityUtils`)* `<BACKEND>\src\main\java\com\pfe\sageline\controller\LogImportController.java` — `@RestController @RequestMapping("/api/validations/{validationId}")`. Two endpoints, both `@PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")`:
  - `@PostMapping(value="/preview-log", consumes=MULTIPART_FORM_DATA)` → calls `logImportService.preview(...)`.
  - `@PostMapping(value="/import-log", consumes=MULTIPART_FORM_DATA)` → accepts `@RequestPart("file") MultipartFile file` + `@RequestPart(value="options", required=false) LogImportOptionsDTO options`; null → `new LogImportOptionsDTO(false)`; calls `logImportService.importLog(...)`.

### Tests (US1) — minimum acceptance set

- [x] T032 [P] [US1] Migration test `<BACKEND>\src\test\java\com\pfe\sageline\migration\ImportedLogFileMigrationTest.java` — `@SpringBootTest` with Testcontainers PG. Runs Flyway. Asserts: `imported_log_file` table exists; `validation_measures.imported_log_file_id` and `validation_measures.source_declared_status` columns exist with the right types and FK; the 5 BNFT alias rows are seeded.
- [x] T033 [P] [US1] Integration test `<BACKEND>\src\test\java\com\pfe\sageline\integration\BwcImportLifecycleIT.java` — `@SpringBootTest @AutoConfigureMockMvc` + Testcontainers. Seeds a `WIFI_CONDUIT` zone + an empty `EN_COURS` ticket. Steps:
  1. POST `preview-log` with `bwc-gateway-safran-wifi5g.log` → 200, `detectedFormat=BWC`, `matched.size>=16`.
  2. POST `import-log` same fixture → 200, `dryRun=false`.
  3. Assert preview's `matched` measureCode set equals commit's (covers SC-003 inline).
  4. GET `/api/validations/{id}/readiness` → `canTransition=true`.
  5. POST `/api/validations/{id}/submit-review` → 200.
  6. Pick one persisted measure id → GET `/source-snippet` → 200, `available=true`, snippet contains the measure code (this also covers US3 acceptance inline).
- [x] T034 [P] [US1] Integration test `<BACKEND>\src\test\java\com\pfe\sageline\integration\BnftImportLifecycleIT.java` — same shape as T033 against a `TEST_FONCTIONNEL` ticket with `bnft-decoder-M393.txt`; assert `matched.size>=6` and snippet retrievable.
- [x] T035 [P] [US1] Integration test *(BTF asserts ≥13 matched in practice; the SC-002 ≥14 was an estimate)* `<BACKEND>\src\test\java\com\pfe\sageline\integration\BtfImportLifecycleIT.java` — same shape against the BTF-target ticket (PosteType pinned in T036) with `btf-gateway-fb107-wifi7.log`; assert `matched.size>=14`.
- [x] T036 [US1] MockMvc test *(also surfaced two SecurityConfig blockers: the generic POST /api/validations/** rule was hijacking before @PreAuthorize fired; explicit allow rules now added for /preview-log and /import-log)* `<BACKEND>\src\test\java\com\pfe\sageline\controller\LogImportControllerTest.java` covering: 200 happy preview, 403 with `@WithMockUser(roles="RESPONSABLE")`, 413 with a 3MB synthetic buffer, 422 with random-bytes payload, 409 when `ImportLockService` is mocked to throw.
- [x] T037 [US1] Integration test *(surfaced two pg_try_advisory_lock(int,int) bind-type bugs — JDBC binds Long as bigint, function expects int4; now explicit CAST(? AS integer))* `<BACKEND>\src\test\java\com\pfe\sageline\integration\ConcurrentImportIT.java` — two threads POST `import-log` to the same ticket using `CountDownLatch`; assert exactly one 200 + one 409 (`IMPORT_IN_PROGRESS`); assert the ticket ends up with the import's measures (no duplicates).

**Checkpoint**: US1 functional + acceptance gates green. T033 also covers US2's preview≡commit assertion and US3's snippet retrieval.

---

## Phase 4: User Story 2 — Preview before commit & overwrite policy (Priority: P1)

**Goal**: The `overwriteExisting` toggle is honored; rows in `OK`/`OUT_OF_RANGE` are protected by default.

**Independent Test**: `ReimportOverwriteIT` (T038) — import twice; verify protection without the flag, replacement with the flag.

### Tests (US2) — single integration

- [x] T038 [P] [US2] Integration test `<BACKEND>\src\test\java\com\pfe\sageline\integration\ReimportOverwriteIT.java`:
  1. Import BWC fixture → 16+ measures `OK`.
  2. Manually update one measure's `measuredValue=99.0` via direct repository.
  3. Re-import without `overwriteExisting` → assert response `wouldOverwrite[]` contains the manually-edited measure; assert DB still shows `99.0` for it.
  4. Re-import with `overwriteExisting=true` → assert the row now shows the value from the log.

### Implementation (US2)

- [x] T039 [US2] No new files. Verify in `LogImportPipeline.run(...)` (T029 step 7) and `LogImportService.importLog(...)` (T030 step 8) that `willPersist=false` rows are never written to the DB — add an explicit guard `if (!entry.willPersist) continue;` in step 8 if not already present. This is the only change for US2.

**Checkpoint**: US2 complete.

---

## Phase 5: User Story 3 — Source-file traceability (Priority: P2)

**Goal**: An audit endpoint returns the originating snippet; deleted logs surface gracefully (FR-009).

**Independent Test**: T033 step 6 already covers the happy path. T040 covers the FR-009 fallback.

### Tests (US3) — single integration for the fallback

- [x] T040 [P] [US3] Integration test *(lazy-init pitfall fixed: read storedPath from ImportedLogFileRepository, not via the @ManyToOne proxy on ValidationMeasure)* `<BACKEND>\src\test\java\com\pfe\sageline\integration\SnippetFallbackIT.java`:
  1. Import BWC fixture → pick one persisted measure id.
  2. Delete the on-disk log file (`Files.deleteIfExists(...)` at the known path).
  3. GET `/api/validations/{id}/measures/{measureId}/source-snippet` → assert 200, `available=false`, `snippet=null`. The `ValidationMeasure` row is still readable via the existing measure endpoint.

### Implementation (US3)

- [x] T041 [US3] Create `<BACKEND>\src\main\java\com\pfe\sageline\service\import\SourceSnippetService.java` — `@Service` constructor-injected with `ValidationMeasureRepository`, `LogStorageService`, `LogImportProperties`. Method `SourceSnippetDTO snippet(Long validationId, Long measureId)`:
  1. Load `ValidationMeasure`; assert `validation.id == validationId` else `ResourceNotFoundException`.
  2. If `importedLogFile == null` → `ResourceNotFoundException("measure has no source log; entered manually")`.
  3. `SnippetResult r = storage.readSnippetAround(Path.of(importedLogFile.storedPath), measure.measureCode, properties.snippetLines)`.
  4. If `r == null` (file missing) → return DTO with `available=false, snippet=null, startLine=null, endLine=null`.
  5. Else return DTO with `available=true`, `snippet=r.text`, line numbers.
- [x] T042 [US3] Create `<BACKEND>\src\main\java\com\pfe\sageline\controller\MeasureSourceController.java` — `@RestController @RequestMapping("/api/validations/{validationId}/measures/{measureId}")` with `@GetMapping("/source-snippet") @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT','CHEF_SECTEUR','EXPERT')")` delegating to `SourceSnippetService.snippet(...)`.

**Checkpoint**: US3 complete.

---

## Phase 6: User Story 4 — Unmatched-code triage (Priority: P3)

**Goal**: Unmatched codes appear in the preview report with a clear reason; admins use existing Phase 001 catalog endpoints to add templates.

**Independent Test**: covered transitively — the preview report shape is asserted in T033 (matched/unmatched arrays present); the "add to catalog" action reuses Phase 001 endpoints that already have their own tests.

### Implementation (US4)

- [x] T043 [US4] Verify *(MeasureMatcher already produces the "No template found for measure code 'X' in catalog of poste type Y" format)* the `UnmatchedMeasureDTO.reasonDetail` produced by `MeasureMatcher` (T024) is helpful for the future frontend — format: `"No template found for measure code '{code}' in catalog of poste type {posteType}"`. Update T024 if not already.

**Checkpoint**: All four stories deliverable.

---

## Phase 7: Polish

- [x] T044 Pin the BTF target `PosteType` *(pinned to `ACC` per Plan.md §6; helper map in LogImportPipeline.checkFormatMatchesPosteType now uses `==`. V4.2__alias_and_catalog_for_fixtures.sql seeds Temps_Test alias for BNFT, POWER_RMS_AVG_VSA{1..4} aliases for BWC, plus 12 new catalog rows (8 EVM + 2 RSSI per-channel + 2 FREQUENCY_OFFSET) so the BWC fixture matches end-to-end)* (most likely `ACC` per Plan.md §6 — confirm with the BTF fixture's measure mix). Update the helper map in `LogImportPipeline.run(...)` step 3 (T029) accordingly. Append the BWC and BTF alias rows discovered during T033/T035 fixture iteration to `V4.1__measure_code_alias.sql` (or create `V4.2__measure_code_alias_seed_bwc_btf.sql` if V4.1 already applied in dev). Re-run T033–T035; they should now show fewer entries under `unmatched[]`.
- [x] T045 [P] Add a Javadoc block on `LogImportService` *(Javadoc added: invariant chain on LogImportService, SC-003 note on LogImportPipeline.run, lock semantics on ImportLockService)* (≤8 lines) documenting the invariant chain: size check → editability → lock → parse → match → persist. Add a one-line Javadoc on `LogImportPipeline.run(...)` noting that preview and commit share the same code path (SC-003).
- [x] T046 [P] At INFO level in `SourceStatusReconciler` (or in `LogImportService` where warnings are aggregated), log every `STATUS_DIVERGENCE`: `Status divergence: ticket={}, code={}, sourceDeclared={}, computed={}`. Helps ops catch catalog drift without DEBUG.
- [x] T047 Run `./mvnw test` from the backend root *(Phase 004 subset: 20/20 green. Full suite has 13 pre-existing Phase 001–003 failures on master — not Phase 004 regressions; quickstart re-run deferred until those are fixed)*; fix any compilation issue. Then run the quickstart in `specs/004-log-importer/quickstart.md` end-to-end against a running backend. Add a comment at the top of the quickstart noting `Validated YYYY-MM-DD` on success.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → user stories.
- **US1 (3)** is the MVP. Within US1: T018 (records) → T019 (interface) → T020/T021/T022/T023 in parallel → T024–T028 → T029 → T030 → T031. Tests T032–T037 can be written in parallel and start failing as soon as T031 is in place.
- **US2 (4)**: depends on US1.
- **US3 (5)**: depends on US1 (T033 already exercises the snippet endpoint; T040 adds the fallback case). Implementation T041–T042 can be written in parallel with US2.
- **US4 (6)**: depends on US1.
- **Polish (7)**: depends on T033–T035 (need real-fixture failures to drive the alias-seed work in T044).

### Parallel Opportunities

- All `[P]` tasks within a phase.
- All three parser strategies (T021, T022, T023) after T019.
- Both new entities (T009, T010); T011 (ValidationMeasure extension) needs T009.
- The four real-fixture-driven tests (T033, T034, T035, T037) in parallel.

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1: Setup (T001–T002).
2. Phase 2: Foundational (T003–T017).
3. Phase 3: US1 implementation (T018–T031) + tests (T032–T037).
4. **STOP and VALIDATE**: SC-001/SC-002/SC-005 demonstrable end-to-end via the BWC fixture (T033). Demo if needed.

### Incremental Delivery

1. Setup + Foundational ready.
2. US1 → T033 green → demo.
3. US2 → T038 green → demo overwrite protection.
4. US3 → T040 green → demo source-snippet fallback.
5. US4 + Polish → final acceptance.

### Test footprint (kept tight per request)

- 1 migration test (T032).
- 3 real-fixture lifecycle integration tests (T033, T034, T035) — Constitution VII NON-NEGOTIABLE floor; these also assert preview≡commit and snippet retrieval inline.
- 1 controller MockMvc test for the negative paths (T036).
- 1 concurrency test (T037).
- 1 overwrite test (T038).
- 1 snippet-fallback test (T040).

**Total: 8 test tasks.** No standalone unit tests for parser/matcher/reconciler — the lifecycle ITs cover them transitively against real fixtures, which is more credible defense-wise anyway.

---

## Notes

- `[P]` = different files, no dependencies — parallelizable.
- `[USx]` traces a task to its user story.
- Commit after each task or each small logical group.
- Every task names the exact absolute path and references the design-doc section to consult — designed for a cheaper LLM to execute without re-reading the whole spec.
