# Phase 1 Data Model: Sagemcom Log Importer

**Feature**: 004-log-importer
**Date**: 2026-05-14
**Inputs**: `spec.md` Key Entities, `research.md` R-004 / R-006 / R-008 / R-009.

This document describes the persisted schema and the DTOs at the controller boundary. The migrations referenced (`V4.0`, `V4.1`) are the binding source — the descriptions below are explanatory.

---

## 1. New entities

### 1.1 `ImportedLogFile`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `validation_id` | `BIGINT` | NOT NULL, FK → `validations(id)` ON DELETE CASCADE | Cascade enforces Clarify Q5 retention rule: delete with the ticket. |
| `original_name` | `VARCHAR(255)` | NOT NULL | The filename the operator uploaded; preserved verbatim for audit. |
| `detected_format` | `VARCHAR(32)` | NOT NULL | `BNFT` / `BWC` / `BTF`. Enum mirrored in Java as `LogFormat`. |
| `stored_path` | `VARCHAR(512)` | NOT NULL | Absolute or storage-root-relative path; canonical layout `{validationId}/{originalName}` (Constitution V). |
| `size_bytes` | `BIGINT` | NOT NULL, ≤ 2 097 152 (CHECK) | The 2 MB cap is enforced in code; the CHECK constraint is defense-in-depth (R-005). |
| `uploaded_by` | `BIGINT` | NOT NULL, FK → `users(id)` | Captured from `SecurityUtils.getCurrentUserId()`. |
| `uploaded_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `now()` | |
| `overwrite_existing` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | The flag the operator passed at commit; persisted for audit of which imports replaced prior measured values (Clarify Q2). |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL | Standard auditing columns wired to `JpaAuditingConfig`. |

**Indexes:** `idx_imported_log_file_validation_id ON (validation_id, uploaded_at DESC)` for snippet lookup by recency.

**Cardinality:** one `Validation` has 0..N `ImportedLogFile`. Each row may produce many `ValidationMeasure` rows (see §1.3).

---

### 1.2 `MeasureCodeAlias`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `poste_type` | `VARCHAR(64)` | NOT NULL | Mirrors the `PosteType` enum string. |
| `source_code` | `VARCHAR(64)` | NOT NULL | The code as it appears in the log. |
| `catalog_measure_code` | `VARCHAR(64)` | NOT NULL | The code in `PosteMeasureCatalog.measure_code` to which it resolves. |
| `active` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | Soft-delete flag (matches Phase 001 catalog convention). |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

**Unique constraint:** `uk_measure_code_alias_poste_source UNIQUE (poste_type, source_code)`.

**Indexes:** unique constraint covers the only lookup pattern (`(posteType, sourceCode)`); no extra indexes.

**Seed (V4.1):** the equivalences explicitly named in `Plan.md` §9, including `(TEST_FONCTIONNEL, MES_BNFT_PWR0_2G) → PWR_2G_ANT0` and the parallel entries for `PWR1_2G`, `PWR0_5G`, `PWR1_5G`, `PWR0_BT`. Additional aliases for BWC and BTF formats will be added during Phase 2 task execution as the fixture parsing surfaces them.

**Cardinality:** independent reference data; not FK-bound to `PosteMeasureCatalog` (intentional — referential drift between an alias and a soft-deleted catalog entry is acceptable since the matcher checks catalog-active separately).

---

## 2. Extensions to existing entities

### 2.1 `ValidationMeasure` — new columns (V4.0)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `imported_log_file_id` | `BIGINT` | NULL, FK → `imported_log_file(id)` ON DELETE SET NULL | Non-null on imported rows, null on manually entered rows. `SET NULL` (not CASCADE) so a disaster-recovery delete of the log row leaves the measure intact and the snippet endpoint falls back to `available: false` (R-010). |
| `source_declared_status` | `VARCHAR(32)` | NULL, CHECK `source_declared_status IS NULL OR source_declared_status IN ('OK_FROM_LOG','OUT_OF_RANGE_FROM_LOG','NOT_EXECUTED_FROM_LOG')` | The Sagemcom Status 0/1/2 from the log. NULL on manually entered rows. Never used by the workflow guard or conformity engine — purely audit signal (R-004). |

**Index:** `idx_validation_measure_imported_log_file ON (imported_log_file_id)` for the snippet lookup path.

**Note on existing `source_log_file` column.** Phase 002 added a `source_log_file VARCHAR(255)` column on `validation_measures` (Constitution V). It is retained as a denormalized convenience (the original filename) and is kept in sync with `imported_log_file.original_name` on every import. The two are *not* redundant: `source_log_file` is the filename string for display; `imported_log_file_id` is the relational anchor for snippet retrieval and cascade behavior. Both are populated together; manual entries leave both NULL.

---

## 3. Java enums

### 3.1 `SourceDeclaredStatus` (new, in `enums/`)

```java
public enum SourceDeclaredStatus {
    OK_FROM_LOG,             // Sagemcom Status 0
    OUT_OF_RANGE_FROM_LOG,   // Sagemcom Status 1
    NOT_EXECUTED_FROM_LOG    // Sagemcom Status 2
}
```

Stored as a `VARCHAR(32)` via `@Enumerated(EnumType.STRING)` per project convention. The naming intentionally diverges from `MeasureStatus` to make leaks visible in code review (Constitution III).

### 3.2 `LogFormat` (new, in `enums/`)

```java
public enum LogFormat { BNFT, BWC, BTF }
```

Surfaces in `ImportedLogFile.detectedFormat` and in `LogImportReportDTO.detectedFormat`.

---

## 4. DTOs

All under `dtos/response/` unless noted. Request shape is multipart, not JSON.

### 4.1 `LogImportReportDTO` (response)

```text
LogImportReportDTO {
  detectedFormat: LogFormat            // BNFT / BWC / BTF
  totalParsed: int                     // matched + unmatched (excludes wouldOverwrite count — those are matched)
  matched: MatchedMeasureDTO[]
  unmatched: UnmatchedMeasureDTO[]
  wouldOverwrite: WouldOverwriteMeasureDTO[]
  warnings: WarningDTO[]
  ticketId: long
  dryRun: boolean                      // true for preview, false for commit
}
```

### 4.2 `MatchedMeasureDTO`

```text
MatchedMeasureDTO {
  measureCode: string                  // catalog measure code (after alias resolution)
  sourceCode: string                   // exactly what was in the log; equals measureCode when no alias used
  measuredValue: double | null
  unit: string
  lowerBound: double
  upperBound: double
  computedStatus: MeasureStatus        // OK / OUT_OF_RANGE / NOT_EXECUTED — authoritative
  sourceDeclaredStatus: SourceDeclaredStatus  // log-level
  templateId: long                     // PosteMeasureCatalog.id
  willPersist: boolean                 // true unless this row needs the overwriteExisting flag and the flag is false
}
```

### 4.3 `UnmatchedMeasureDTO`

```text
UnmatchedMeasureDTO {
  measureCode: string                  // as it appeared in the log
  measuredValue: double | null
  unit: string | null
  reason: enum { NO_TEMPLATE_FOR_POSTE_TYPE, AMBIGUOUS_ALIAS, MISSING_BOUNDS_IN_LOG }
  reasonDetail: string                 // human-readable detail
}
```

### 4.4 `WouldOverwriteMeasureDTO`

```text
WouldOverwriteMeasureDTO {
  measureCode: string
  currentValue: double | null
  currentStatus: MeasureStatus         // OK or OUT_OF_RANGE (NOT_EXECUTED rows go to matched, not here)
  currentEnteredManually: boolean      // true when the existing row has no imported_log_file_id
  newValue: double | null
  newComputedStatus: MeasureStatus
}
```

### 4.5 `WarningDTO`

```text
WarningDTO {
  code: enum { WRONG_STATION_FORMAT, STATUS_DIVERGENCE, MISSING_UNIT_FALLBACK, TRUNCATED_FINAL_BLOCK }
  measureCode: string | null           // null for whole-file warnings (e.g., WRONG_STATION_FORMAT)
  message: string
}
```

### 4.6 `SourceSnippetDTO`

```text
SourceSnippetDTO {
  measureId: long
  originalFilename: string
  detectedFormat: LogFormat
  snippet: string | null               // null iff available == false
  available: boolean                   // false when file missing on disk (FR-009 fallback)
  startLine: int | null
  endLine: int | null
}
```

### 4.7 `LogImportOptionsDTO` (request, multipart form fields)

```text
LogImportOptionsDTO {
  overwriteExisting: boolean           // default false
}
```

Bound via `@RequestPart("options") LogImportOptionsDTO options` alongside `@RequestPart("file") MultipartFile file`.

---

## 5. Service-layer value objects (not DTOs)

These never cross the HTTP boundary; they are the parser's contract.

### 5.1 `ParsedLog`

```java
record ParsedLog(
    LogFormat format,
    List<ParsedMeasure> measures,
    List<String> parserNotes      // free-text from the strategy, surfaced into warnings as TRUNCATED_FINAL_BLOCK etc.
) {}
```

### 5.2 `ParsedMeasure`

```java
record ParsedMeasure(
    String code,
    String label,
    SourceDeclaredStatus sourceStatus,
    Double lowerBound,
    Double upperBound,
    String unit,
    Double measuredValue          // null when Status 2 in source
) {}
```

### 5.3 `LogImportCommand`

```java
record LogImportCommand(
    Long validationId,
    String originalFilename,
    Path storedPath,
    byte[] bytesForParsing,       // held in memory only during the pipeline; not persisted
    boolean overwriteExisting,
    Long uploadedBy
) {}
```

---

## 6. State transitions

This phase introduces no new `Validation` status transitions. The relevant lifecycle is:

```
ticket EN_COURS
  ├── operator POST /preview-log    → report rendered, no DB writes
  ├── operator POST /import-log     → matched rows upserted; wouldOverwrite[] persisted iff overwriteExisting=true
  │                                   ImportedLogFile row created
  │                                   readiness snapshot fires on AFTER_COMMIT
  └── (any other state)             → MeasureEditabilityGuard rejects with 422
```

Re-imports remain idempotent by `measureCode` (R-003). Concurrent imports on the same ticket are serialized by advisory lock (R-007).

---

## 7. Migrations

### `V4.0__imported_log_file.sql`

- Create `imported_log_file` table per §1.1.
- Add `imported_log_file_id BIGINT NULL` + `source_declared_status VARCHAR(32) NULL` columns on `validation_measures`.
- Add FK and check constraints per §1.1 and §2.1.
- Create `idx_imported_log_file_validation_id` and `idx_validation_measure_imported_log_file`.

### `V4.1__measure_code_alias.sql`

- Create `measure_code_alias` table per §1.2.
- Add `uk_measure_code_alias_poste_source` unique constraint.
- Seed rows for the BNFT aliases named in `Plan.md` §9; BWC and BTF aliases pinned during Phase 2 task execution (R-008) and committed as additional `INSERT` statements in the same migration (or as `V4.2__measure_code_alias_seed_bwc_btf.sql` if discovered late — both are acceptable per Flyway versioning rules).

Both migrations are forward-only and idempotent given Flyway's checksum guards. Rollback notes: dropping `validation_measures.imported_log_file_id` requires removing all rows that reference it first; this is documented in the migration header but not scripted (the project's policy is forward-only).
