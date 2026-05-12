# Data Model — Phase 002 ValidationMeasure

## Entity: `ValidationMeasure`

Persisted in table `validation_measures`. JPA entity under `com.pfe.sageline.entity.ValidationMeasure`.

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `id` | `Long` | no | PK, identity. |
| `validation_id` | `Long` (FK → `validations.id`) | no | Owning ticket. `ON DELETE CASCADE` — deleting a ticket deletes its measures. |
| `catalog_template_id` | `Long` (FK → `poste_measure_catalog.id`) | yes | Set when the measure was instantiated from or aligned with a catalog template. `NULL` for ad-hoc measures (US4). `ON DELETE SET NULL` — soft-deleting (`active=false`) the catalog template does not break existing measures. |
| `measure_code` | `VARCHAR(64)` | no | Industrial code. Copied from template at instantiation; supplied directly for ad-hoc. |
| `measure_label` | `VARCHAR(255)` | no | Human-readable label. |
| `category` | `VARCHAR(32)` (enum `MeasureCategory`) | no | Enum from Phase 001. |
| `unit` | `VARCHAR(16)` | no | E.g., `dBm`, `mA`, `V`. |
| `lower_bound` | `DOUBLE PRECISION` | no | Inclusive. Must be `< upper_bound` (DTO validator). |
| `upper_bound` | `DOUBLE PRECISION` | no | Inclusive. |
| `measured_value` | `DOUBLE PRECISION` | yes | `NULL` ⇒ `status = NOT_EXECUTED`. |
| `status` | `VARCHAR(32)` (enum `MeasureStatus`) | no | Recomputed on every write by `MeasureDeviationCalculator`. |
| `deviation_pct` | `DOUBLE PRECISION` | yes | `NULL` when `measured_value` is `NULL`; otherwise computed (0–∞). |
| `antenna` | `VARCHAR(16)` | yes | Physical context (RF measures). |
| `frequency_mhz` | `INTEGER` | yes | Physical context (RF measures). |
| `modulation_scheme` | `VARCHAR(32)` | yes | Physical context (RF measures). |
| `source_log_file` | `VARCHAR(255)` | yes | Filename under `storage/logs/{validationId}/`. Populated by Phase 004 importer or by V2.1 migration; **clients cannot set this field in Phase 002**. |
| `entered_by` | `Long` (FK → `users.id`) | no on insert | The operator stamped on every write. Refreshed on update (R9). |
| `measured_at` | `TIMESTAMP` | no | Refreshed on every write. |
| `created_at` | `TIMESTAMP` | no | `@CreatedDate`. |
| `updated_at` | `TIMESTAMP` | no | `@LastModifiedDate`. |

### Indexes & constraints

- `pk_validation_measures` — primary key on `id`.
- `fk_vm_validation` — FK `validation_id → validations(id)` ON DELETE CASCADE.
- `fk_vm_catalog` — FK `catalog_template_id → poste_measure_catalog(id)` ON DELETE SET NULL.
- `fk_vm_entered_by` — FK `entered_by → users(id)`.
- `ix_vm_validation` — non-unique index on `validation_id` (every list query filters by this).
- `ix_vm_measure_code` — non-unique index on `measure_code` (Phase 006 KPI rollups).
- `uq_vm_natural_key` — **unique** index on
  `(validation_id, measure_code, COALESCE(antenna,''), COALESCE(frequency_mhz,-1), COALESCE(modulation_scheme,''))`.
  Enforces R7: one measure per (ticket, code, physical context).
- `ck_vm_bounds` — `CHECK (lower_bound < upper_bound)`.
- `ck_vm_deviation_consistency` — `CHECK ((measured_value IS NULL AND status='NOT_EXECUTED' AND deviation_pct IS NULL) OR (measured_value IS NOT NULL AND status IN ('OK','OUT_OF_RANGE') AND deviation_pct IS NOT NULL))`.

### Validation rules (DTO layer)

- `measureCode` — non-blank, ≤ 64 chars, matches `[A-Z0-9_]+`.
- `unit` — non-blank, ≤ 16 chars.
- `lowerBound` < `upperBound` (strict, enforced by class-level `@AssertTrue`).
- `frequencyMhz` — optional; when present, `>= 0`.
- `antenna`, `modulationScheme` — optional; when present, trimmed non-blank.
- `sourceLogFile` — **rejected on client requests** (FR-013, R8): if non-null, return 400.
- `templateId` — optional; when present, the catalog template MUST exist and MUST be `active = true` and MUST match the ticket's zone poste type. Otherwise 400 / 404.

### State lifecycle

A `ValidationMeasure` has no lifecycle of its own; its mutability follows the owning ticket's status:

| Ticket status | Create | Update | Delete | Read |
|---------------|--------|--------|--------|------|
| `PLANIFIE`, `EN_ATTENTE_PREP`, `PREP_VALIDEE` | ❌ 422 | ❌ 422 | ❌ 422 | ✅ |
| `EN_COURS` | ✅ | ✅ | ✅ | ✅ |
| `EN_ATTENTE_HANDOVER` | ❌ 422 | ❌ 422 | ❌ 422 | ✅ |
| `EN_REVUE` | ❌ 422 | ❌ 422 | ❌ 422 | ✅ |
| `CONFORME`, `NON_CONFORME`, `ANNULE` | ❌ 422 | ❌ 422 | ❌ 422 | ✅ |

Enforced by `MeasureEditabilityGuard` (single entry point; FR-015; clarification Q2).

### Computed-field rules

`MeasureDeviationCalculator` is the **only** producer of `status` and `deviation_pct`. Service-layer creates and updates always invoke it after binding fields from the request and before persisting. DTOs **do not accept** `status` or `deviationPct` on input — any value supplied by the client is silently ignored.

```
center    = (lowerBound + upperBound) / 2
halfRange = (upperBound - lowerBound) / 2     ; halfRange > 0 (enforced by DTO)
status    = NOT_EXECUTED                        if measured == null
          = OK                                  if lowerBound <= measured <= upperBound
          = OUT_OF_RANGE                        otherwise
deviation = null                                if measured == null
          = abs(measured - center) / halfRange * 100
```

### Concurrency

Last-writer-wins (R4). No `@Version` column. Every write refreshes `entered_by` and `measured_at` (R9). The natural-key unique index (`uq_vm_natural_key`) is the only DB-level conflict detector and exists to prevent duplicate catalog instantiation, not concurrent updates.

## Entity: `ValidationResult` (existing — UNCHANGED)

Phase 002 does **not** modify the existing `validation_results` table beyond adding a `migrated_at TIMESTAMP NULL` audit column (V2.2). The legacy `parameter` / `expected_value` / `conform` columns remain in place exactly as today; no rows are deleted or transformed.

## Data migration (V2.1)

For every row in `validation_results` where `migrated_at IS NULL`, insert one row into `validation_measures`:

| Source (`validation_results`) | Target (`validation_measures`) |
|-------------------------------|-------------------------------|
| `parameter` | `measure_code` (uppercased, non-word chars → `_`) and `measure_label` (verbatim) |
| `measured_value` | `measured_value` |
| `expected_value` | `lower_bound = LEAST(v*0.95, v*1.05)`, `upper_bound = GREATEST(v*0.95, v*1.05)`; when `expected_value = 0`, `lower_bound = -0.5`, `upper_bound = 0.5` (clarification Q5; R2). The `LEAST/GREATEST` wrapper is mandatory for negative `expected_value` (e.g. dBm readings): with `v = -50`, the naive `v*0.95 = -47.5` exceeds `v*1.05 = -52.5`, which would violate `ck_vm_bounds`. |
| (none) | `unit = ''` then post-update set to `'unknown'` (legacy rows had no unit) |
| (none) | `category = 'OTHER'` |
| `conform = true` | `status = 'OK'` (forced from legacy `conform` per clarification Q5); `deviation_pct` computed from the synthesized window |
| `conform = false` | `status = 'OUT_OF_RANGE'` (forced from legacy `conform`); `deviation_pct` computed from the synthesized window |
| `measured_value IS NULL` | `status = 'NOT_EXECUTED'`; `deviation_pct = NULL` (defense-in-depth against future legacy-schema relaxations; today's legacy schema declares the column NOT NULL) |
| `validation_id` | `validation_id` |
| `created_at` | `measured_at`, `created_at` |
| (system) | `entered_by = NULL` for migrated rows (no operator identity available); `updated_at = now()`; `source_log_file = NULL` |

The migration is implemented as a SQL `INSERT … SELECT` with the deviation arithmetic inline (Postgres expressions), then a single UPDATE on `validation_results` to set `migrated_at = now()` for the rows just copied. The whole step runs in a single transaction. The `ck_vm_deviation_consistency` CHECK constraint is the integrity backstop.

> Note on the `entered_by` exception: the new entity declares `entered_by` non-null at the JPA level for *new* writes (every API write must produce an audited row), but the column itself is **nullable** in the schema (V2.0) to accommodate the historical migration rows that have no recoverable operator identity. New rows always set it.
