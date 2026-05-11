# Phase 1 — Data Model

**Feature**: 001-poste-type-catalog
**Date**: 2026-05-11

## Entities

### `PosteMeasureCatalog`

JPA entity. Table: `poste_measure_catalog`.

| Field | Java type | DB column | DB type | Nullable | Notes |
|-------|-----------|-----------|---------|----------|-------|
| `id` | `Long` | `id` | `BIGSERIAL` | NO (PK) | Identity. |
| `posteType` | `PosteType` (enum) | `poste_type` | `VARCHAR(64)` | NO | Persisted as `EnumType.STRING`. Indexed (see below). |
| `measureCode` | `String` | `measure_code` | `VARCHAR(64)` | NO | Validated by regex `^[A-Z][A-Z0-9_]{1,63}$` (R-012). |
| `measureLabel` | `String` | `measure_label` | `VARCHAR(255)` | NO | Human-readable French/English label from source log. |
| `category` | `MeasureCategory` (enum) | `category` | `VARCHAR(32)` | NO | `EnumType.STRING`. |
| `defaultUnit` | `String` | `default_unit` | `VARCHAR(16)` | NO | e.g., `dBm`, `mA`, `V`, `dB`, `°C`, `s`, `MHz`, `%`. |
| `defaultLowerBound` | `BigDecimal` | `default_lower_bound` | `NUMERIC(18,6)` | NO | Inclusive lower bound. |
| `defaultUpperBound` | `BigDecimal` | `default_upper_bound` | `NUMERIC(18,6)` | NO | Inclusive upper bound. Service-layer check: `lower < upper`. |
| `mandatory` | `boolean` | `mandatory` | `BOOLEAN` | NO | Default `false`. Snapshotted by Phase 002 at ticket creation. |
| `displayOrder` | `int` | `display_order` | `INTEGER` | NO | Default `0`. Used by sort-by-order reads. |
| `active` | `boolean` | `active` | `BOOLEAN` | NO | Default `true`. Soft-delete flag. |
| `antenna` | `String` | `antenna` | `VARCHAR(16)` | YES | Optional RF context (e.g., `ANT1`). |
| `frequencyMhz` | `Integer` | `frequency_mhz` | `INTEGER` | YES | Optional RF context (e.g., `5500`). |
| `modulationScheme` | `String` | `modulation_scheme` | `VARCHAR(32)` | YES | Optional RF context (e.g., `MCS9`, `OFDM`). |
| `createdAt` | `Instant` | `created_at` | `TIMESTAMPTZ` | NO | `@CreatedDate`. |
| `createdBy` | `Long` | `created_by` | `BIGINT` | YES | `@CreatedBy`. Null for seed rows (no auth context during Flyway). |
| `updatedAt` | `Instant` | `updated_at` | `TIMESTAMPTZ` | NO | `@LastModifiedDate`. |
| `updatedBy` | `Long` | `updated_by` | `BIGINT` | YES | `@LastModifiedBy`. Null until a curator first touches the row. |

**Annotations:**
- `@Entity`, `@Table(name = "poste_measure_catalog")`
- `@EntityListeners(AuditingEntityListener.class)`
- Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- `@Enumerated(EnumType.STRING)` on `posteType` and `category`

**Lifecycle states:**

```
            create (POST)
                │
                ▼
      ┌─────────────────┐  update (PUT) ┌─────────────────┐
      │  ACTIVE         │  ───────────► │  ACTIVE         │
      │  active=true    │  ◄───────────  │  active=true    │
      └─────────────────┘  reactivate    └─────────────────┘
                │ ▲                            │
                │ │ PUT {active:true}          │ soft-delete (DELETE)
   soft-delete  │ │ + bounds                   │
   (DELETE)     │ │                            ▼
                ▼ │                  ┌─────────────────┐
      ┌─────────────────┐            │  INACTIVE       │
      │  INACTIVE       │ ────────►  │  active=false   │
      │  active=false   │            └─────────────────┘
      └─────────────────┘
```

`PUT` on an inactive template may flip `active=true` (reactivation path used by the
"seed inactive" contingency in R-009).

### `MeasureCategory` (enum)

`com.pfe.sageline.enums.MeasureCategory`. Constants:
`POWER, VOLTAGE, CURRENT, FREQUENCY, TIME, TEMPERATURE, PER, RSSI, EVM, OTHER`.

### `MeasureStatus` (enum, ahead-of-time for Phase 002)

`com.pfe.sageline.enums.MeasureStatus`. Constants with explicit Sagemcom mapping:

| Constant | Sagemcom Status | Meaning |
|----------|------------------|---------|
| `OK` | 0 | Measured value falls within `[lowerBound, upperBound]`. |
| `OUT_OF_RANGE` | 1 | Measured value outside the bounds. |
| `NOT_EXECUTED` | 2 | Measure not yet performed; first-class state, used by workflow guard. |

Defined here so Phase 002's `ValidationMeasure` can reference it without churn.

### `PosteType` (existing enum — UNCHANGED)

Already exists at `com.pfe.sageline.enums.PosteType` with 22 constants. No edits in this phase.

## Constraints & Indexes

```sql
-- Primary key
ALTER TABLE poste_measure_catalog ADD PRIMARY KEY (id);

-- Partial unique index — only active rows are uniqueness-constrained
CREATE UNIQUE INDEX uk_poste_measure_catalog_active
    ON poste_measure_catalog (poste_type, measure_code)
    WHERE active = true;

-- Read-path indexes
CREATE INDEX idx_poste_measure_catalog_poste_type
    ON poste_measure_catalog (poste_type, display_order)
    WHERE active = true;

CREATE INDEX idx_poste_measure_catalog_code
    ON poste_measure_catalog (measure_code)
    WHERE active = true;

-- Bounds sanity check (defense-in-depth, in addition to service-layer check)
ALTER TABLE poste_measure_catalog
    ADD CONSTRAINT chk_poste_measure_catalog_bounds
    CHECK (default_lower_bound < default_upper_bound);
```

## Relationships

- **None in this phase.** The catalog is a standalone reference table.
- **Forward references** (for Phase 002+):
  - `ValidationMeasure.catalogTemplateId` → `poste_measure_catalog.id` (nullable FK; ad-hoc measures have null).
  - `ValidationMeasure.mandatoryAtCreation` snapshots `poste_measure_catalog.mandatory` at ticket open (R-005).

## DTO Shapes

### Request: `PosteMeasureCatalogRequest`

```java
public record PosteMeasureCatalogRequest(
    @NotNull PosteType posteType,
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String measureCode,
    @NotBlank @Size(max = 255) String measureLabel,
    @NotNull MeasureCategory category,
    @NotBlank @Size(max = 16) String defaultUnit,
    @NotNull BigDecimal defaultLowerBound,
    @NotNull BigDecimal defaultUpperBound,
    boolean mandatory,
    Integer displayOrder,           // null ⇒ default 0
    @Size(max = 16) String antenna,
    @Min(0) Integer frequencyMhz,
    @Size(max = 32) String modulationScheme
) {}
```

Service-layer validator additionally enforces `defaultLowerBound.compareTo(defaultUpperBound) < 0` and returns HTTP 422 on violation (FR-005).

### Request: `PosteMeasureCatalogUpdateRequest`

Same as `PosteMeasureCatalogRequest` **without** `posteType` and `measureCode` (immutable per FR-011). All other fields optional (partial update). Adds `Boolean active` (nullable; set explicitly to flip the soft-delete state).

### Request: `PosteMeasureCatalogBatchRequest`

```java
public record PosteMeasureCatalogBatchRequest(
    @NotNull PosteType posteType,
    @NotEmpty @Size(max = 100) List<@Valid BatchItem> items
) {
    public record BatchItem(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String measureCode,
        @NotBlank @Size(max = 255) String measureLabel,
        @NotNull MeasureCategory category,
        @NotBlank @Size(max = 16) String defaultUnit,
        @NotNull BigDecimal defaultLowerBound,
        @NotNull BigDecimal defaultUpperBound,
        boolean mandatory,
        Integer displayOrder,
        String antenna,
        Integer frequencyMhz,
        String modulationScheme
    ) {}
}
```

### Response: `PosteMeasureCatalogResponse`

```java
public record PosteMeasureCatalogResponse(
    Long id,
    PosteType posteType,
    String measureCode,
    String measureLabel,
    MeasureCategory category,
    String defaultUnit,
    BigDecimal defaultLowerBound,
    BigDecimal defaultUpperBound,
    boolean mandatory,
    int displayOrder,
    boolean active,
    String antenna,
    Integer frequencyMhz,
    String modulationScheme,
    Instant createdAt,
    Long createdBy,
    Instant updatedAt,
    Long updatedBy
) {}
```

> **Snapshot note (Phase 002 consumer):** the four fields `mandatory`, `defaultLowerBound`, `defaultUpperBound`, `defaultUnit` MUST be copied onto the `ValidationMeasure` at ticket-creation time. The workflow guard MUST read the snapshot, not re-query the catalog (R-005).

## Validation Rules Mapped to FRs

| FR | Rule | Where enforced |
|----|------|----------------|
| FR-002 | All required fields non-null | Jakarta Bean Validation on request DTO |
| FR-003 | Optional context fields nullable | Schema + DTO (no `@NotNull`) |
| FR-004 | `(posteType, measureCode)` unique among active | Partial unique index + pre-check in service → HTTP 409 |
| FR-005 | `lower < upper` | Service `BoundsValidator` + DB check constraint → HTTP 422 |
| FR-011 | `posteType` and `measureCode` immutable | Update DTO does not expose them |
| FR-012 | Soft delete | Service sets `active=false`; row preserved |
| FR-013 | Atomic batch | `@Transactional` service method |
| FR-014 | Seed counts | Flyway `V1.2__seed_poste_catalog.sql` |
| FR-015 | Idempotent seed | Flyway + `ON CONFLICT DO NOTHING` |
| FR-016 | Reads open to authenticated users | Method-level `@PreAuthorize("isAuthenticated()")` |
| FR-017 | Authentication required | `SecurityConfig` baseline + missing-JWT → 401 |
| FR-018 | Validation 422 with field details | `GlobalExceptionHandler` mapping for `MethodArgumentNotValidException` + `BoundsViolationException` |
| FR-020 | DTO separation | Mapper layer, controller uses DTOs only |
| FR-022 | Surface current `mandatory` + bounds + unit | Response DTO fields above |
| FR-023 | Audit quartet | `AuditingEntityListener` + nullable actor columns |
