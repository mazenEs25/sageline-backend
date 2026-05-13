# Phase 1 Data Model: Workflow Guard

This phase introduces **no new tables**. It adds **one column** to `validation_measures` and defines several response DTOs that exist only at the REST/STOMP boundary (Constitution VI).

## 1. Schema change: `validation_measures.mandatory_at_creation`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `mandatory_at_creation` | `BOOLEAN` | NO (default `FALSE`) | Snapshot of the catalog template's `mandatory` flag at the moment the measure was inserted. Never updated after insert. Read by the workflow guard as the single source of truth for "is this measure mandatory for this ticket." |

**Migration**: `V3.0__validation_measure_mandatory_snapshot.sql`

```sql
ALTER TABLE validation_measures
    ADD COLUMN mandatory_at_creation BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE validation_measures vm
SET    mandatory_at_creation = COALESCE(c.mandatory, FALSE)
FROM   poste_measure_catalog c
WHERE  vm.catalog_template_id = c.id;

CREATE INDEX idx_vm_validation_mandatory
    ON validation_measures (validation_id, mandatory_at_creation);
```

**Index rationale.** The readiness query is "for ticket X, count measures grouped by `(mandatory_at_creation, status)`." A composite index on `(validation_id, mandatory_at_creation)` lets PostgreSQL satisfy the count with an index scan even when a ticket has 100+ measures (SC-003 budget).

**Entity change** (`com.pfe.sageline.entity.ValidationMeasure`):

```java
@Column(name = "mandatory_at_creation", nullable = false)
private boolean mandatoryAtCreation;
```

`ValidationMeasureServiceImpl.create*()` MUST stamp this field at insert time:
- If `catalogTemplate != null` → `entity.setMandatoryAtCreation(catalogTemplate.isMandatory())`
- Else (ad-hoc measure) → `entity.setMandatoryAtCreation(false)`

The field MUST NOT be part of any update DTO and MUST NOT be writable through the REST API.

## 2. Read-only DTOs (no persistence)

### 2.1 `WorkflowReadinessDTO`

```java
public record WorkflowReadinessDTO(
    Long ticketId,
    String currentStatus,                       // e.g., "EN_COURS"
    String targetStatus,                        // e.g., "EN_REVUE"
    int mandatoryTotal,
    int mandatoryFilled,
    int mandatoryMissing,
    List<MissingMeasureDTO> missingMeasures,    // size == mandatoryMissing
    List<OutOfRangeMeasureDTO> outOfRangeMeasures,
    boolean canTransition,
    List<String> blockingReasons                // empty when canTransition == true
) {}
```

**Invariants** (asserted by `WorkflowReadinessServiceTest`):
- `mandatoryFilled + mandatoryMissing == mandatoryTotal`
- `missingMeasures.size() == mandatoryMissing`
- `canTransition == blockingReasons.isEmpty()`
- When `canTransition == true`, `mandatoryMissing == 0` (the converse is not required — other rules may still block when coverage is met)

### 2.2 `MissingMeasureDTO`

```java
public record MissingMeasureDTO(
    String measureCode,    // e.g., "POWER_RMS_AVG_VSA1_ANT3_5670"
    String label,
    boolean required       // always true in this phase; reserved for future "soft-required" semantics
) {}
```

### 2.3 `OutOfRangeMeasureDTO`

```java
public record OutOfRangeMeasureDTO(
    String measureCode,
    String label,
    Double measuredValue,
    Double lowerBound,
    Double upperBound,
    Double deviationPct
) {}
```

## 3. State / lifecycle

This phase does not introduce new entity lifecycles. It introduces a **transition predicate** over the existing `TicketStatus` graph:

```
EN_COURS ──[guard.allow]──▶ EN_REVUE
   │
   └──[guard.block]──▶ EN_COURS  (no state change; 422+WorkflowReadinessDTO)
```

Guard sequencing for the `(EN_COURS, EN_REVUE)` pair (executed in order; first-failure-wins for refusal text but ALL failures are aggregated into `blockingReasons` so the user sees the complete picture in one round-trip):

1. `SourceStatusRule` — refuse if `currentStatus != EN_COURS`. (Edge case: probe-on-non-EN_COURS ticket.)
2. `LegacyChecksAdapter.role` — delegate to existing `@PreAuthorize` logic; refuse with "user lacks role TECH_VAL/ADMIN_IT" if it would have blocked.
3. `LegacyChecksAdapter.handover` — delegate to existing FR-006a check (allow original tech to submit even from `EN_ATTENTE_HANDOVER`); never refuses, only widens `currentStatus` acceptance.
4. `MandatoryMeasureCoverageRule` — refuse if any `validation_measures` row for this ticket has `mandatory_at_creation = true AND status = 'NOT_EXECUTED'`; populate `missingMeasures`.

`outOfRangeMeasures` is computed unconditionally (informational) by the readiness service after rule sequencing, regardless of `canTransition`. It is never a `blockingReason` in this phase (FR-008).

## 4. Repository projections

`ValidationMeasureRepository` gains two read-only projections (no entity returns; DTO projections per Constitution VI):

```java
// For the count summary (mandatoryTotal / mandatoryFilled).
@Query("""
       SELECT new com.pfe.sageline.dtos.internal.MandatoryCoverageRow(
              vm.mandatoryAtCreation,
              vm.status,
              COUNT(vm))
       FROM   ValidationMeasure vm
       WHERE  vm.validation.id = :validationId
       GROUP  BY vm.mandatoryAtCreation, vm.status
       """)
List<MandatoryCoverageRow> coverageSummary(@Param("validationId") Long validationId);

// For the missing list (only the rows that block).
@Query("""
       SELECT new com.pfe.sageline.dtos.response.MissingMeasureDTO(
              vm.measureCode, vm.measureLabel, true)
       FROM   ValidationMeasure vm
       WHERE  vm.validation.id      = :validationId
         AND  vm.mandatoryAtCreation = true
         AND  vm.status              = com.pfe.sageline.enums.MeasureStatus.NOT_EXECUTED
       ORDER  BY vm.measureCode
       """)
List<MissingMeasureDTO> missingMandatoryMeasures(@Param("validationId") Long validationId);
```

Both queries hit the new `idx_vm_validation_mandatory` index. No `JOIN FETCH` needed because neither projection traverses associations — the DTO carries primitives only.

`MandatoryCoverageRow` is an internal carrier (not a public response DTO) under `dtos/internal/`; it's collapsed into the public `WorkflowReadinessDTO` by `WorkflowReadinessMapper`.
