# Phase 1 — Data Model

**Feature**: Shift-End Ticket Handover (`001-ticket-handover`)
**Date**: 2026-05-05
**Source**: derived from `spec.md` §Key Entities and `Plan.md` §Phase 2

This document is the authoritative data-model contract for the
backend. Frontend types are derived from `contracts/handover-rest-api.md`.

---

## 1. New entity — `TicketHandover`

**Table**: `ticket_handovers`
**Lifecycle**: `PENDING → ACCEPTED → COMPLETED` (happy path) or
`PENDING → CANCELLED` (supervisor cancel) or `* → CANCELLED` (auto
on early closure).

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `Long` | NO | `@Id @GeneratedValue(IDENTITY)` |
| `validation` | `Validation` | NO | `@ManyToOne(LAZY)` → `validation_id` |
| `fromTech` | `User` | NO | `@ManyToOne(LAZY)` → `from_tech_id` |
| `toTech` | `User` | YES | `@ManyToOne(LAZY)` → `to_tech_id`. Null until assigned/accepted. |
| `handoverNote` | `String` | YES | `@Column(columnDefinition = "TEXT")` |
| `progressSummary` | `String` | YES | `@Column(columnDefinition = "TEXT")` |
| `status` | `HandoverStatus` | NO | `@Enumerated(STRING)`, default `PENDING` |
| `triggeredBy` | `TriggerType` | NO | `@Enumerated(STRING)` |
| `scheduledAt` | `LocalDateTime` | NO | Set on creation; "creation timestamp" in spec |
| `acceptedAt` | `LocalDateTime` | YES | Set on `PENDING/ACCEPTED → COMPLETED` |

**Lombok**: `@Data @NoArgsConstructor @AllArgsConstructor @Builder`.

**Invariants** (enforced in `HandoverService`):
1. A validation MAY have at most one `TicketHandover` with status
   in (`PENDING`, `ACCEPTED`) at any time. Enforced by JPQL
   `NOT EXISTS` filter (R6) and by the optimistic status-conditional
   UPDATE on transitions (R1).
2. `acceptedAt` is non-null **iff** `status = COMPLETED`.
3. `toTech` becomes non-null at `ACCEPTED` (assigned by supervisor)
   or `COMPLETED` (self-accept jumps `PENDING → COMPLETED` and
   sets `toTech = currentUser` in the same transaction).
4. `triggeredBy` is immutable after creation.

**State transitions** (allowed):

```text
            initiate*
   (none) ─────────────► PENDING
                          │   │
              assign      │   │ accept (self-accept by TECH_VAL)
            (supervisor)  │   │
                          ▼   ▼
                       ACCEPTED ──── accept (designated tech) ──► COMPLETED
                          │
                          │ cancel (supervisor)
                          │  or  autoCancelIfPending (closure)
                          ▼
                       CANCELLED
```

`*` initiate paths: manual (`TECH_VAL`), auto
(`ShiftEndHandoverJob` → `SHIFT_END_AUTO`), force
(`ADMIN_IT/CHEF_SECTEUR` → `ADMIN_FORCE`).

---

## 2. New enum — `HandoverStatus`

```java
package com.pfe.sageline.enums;

public enum HandoverStatus {
    PENDING,    // awaiting assignment or self-accept
    ACCEPTED,   // a TECH_VAL has been designated, not yet confirmed
    COMPLETED,  // new tech is active, ticket resumed
    CANCELLED   // supervisor or auto-cancel
}
```

---

## 3. New enum — `TriggerType`

```java
package com.pfe.sageline.enums;

public enum TriggerType {
    MANUAL,          // TECH_VAL self-initiated
    SHIFT_END_AUTO,  // ShiftEndHandoverJob at 16:45
    ADMIN_FORCE      // CHEF_SECTEUR or ADMIN_IT override
}
```

---

## 4. Modified enum — `TicketStatus`

Add **one** value, between `EN_COURS` and `EN_REVUE` semantically:

```java
public enum TicketStatus {
    PLANIFIE,
    EN_PREP,
    PRET,
    EN_COURS,
    EN_ATTENTE_HANDOVER,   // ← NEW
    EN_REVUE,
    CONFORME,
    NON_CONFORME,
    ANNULE
}
```

**Allowed entry/exit transitions** (extending the existing state
machine):

- `EN_COURS → EN_ATTENTE_HANDOVER` — on any handover initiation.
- `EN_ATTENTE_HANDOVER → EN_COURS` — on handover acceptance (new
  tech) or supervisor cancellation (original tech).
- `EN_ATTENTE_HANDOVER → EN_REVUE | CONFORME | NON_CONFORME` —
  only when the **original technician** invokes a terminal
  transition (FR-006a) which auto-cancels the pending handover.

While in `EN_ATTENTE_HANDOVER`, no `ValidationResult` may be
persisted, no review may be requested, and the ticket may not be
closed by anyone other than the original technician (FR-006).

---

## 5. Modified enum — `AssignmentStatus`

Add `PAUSED`:

```java
public enum AssignmentStatus {
    ACTIVE,
    PAUSED,      // ← NEW — outgoing tech of a handover
    COMPLETED,
    CANCELLED
}
```

`PAUSED` rows are never deleted (FR-011). Cancelling a handover
re-activates the original assignment by flipping
`PAUSED → ACTIVE`.

---

## 6. Modified entity — `ValidationAssignment`

Add a single field:

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `handoverNote` | `String` | YES | `@Column(columnDefinition = "TEXT")` — what the outgoing tech accomplished. Set on the **outgoing** assignment when paused. |

No relationship changes. No FK to `TicketHandover` (the link is
through `validation`).

---

## 7. Repository contracts

### `HandoverRepository extends JpaRepository<TicketHandover, Long>`

```java
// Find the active (non-terminal) handover for a ticket, if any.
@Query("""
    SELECT h FROM TicketHandover h
    WHERE  h.validation.id = :validationId
    AND    h.status IN ('PENDING','ACCEPTED')
""")
Optional<TicketHandover> findActiveByValidation(@Param("validationId") Long validationId);

// Full chronological history for ticket detail view.
@Query("""
    SELECT h FROM TicketHandover h
    LEFT JOIN FETCH h.fromTech
    LEFT JOIN FETCH h.toTech
    WHERE  h.validation.id = :validationId
    ORDER BY h.scheduledAt ASC
""")
List<TicketHandover> findByValidationOrderByScheduledAtAsc(@Param("validationId") Long validationId);

// All currently pending handovers (queue panel for sector leads / admins).
@Query("""
    SELECT h FROM TicketHandover h
    LEFT JOIN FETCH h.validation v
    LEFT JOIN FETCH v.productionLine
    LEFT JOIN FETCH h.fromTech
    LEFT JOIN FETCH h.toTech
    WHERE  h.status = 'PENDING'
    ORDER BY h.scheduledAt ASC
""")
List<TicketHandover> findAllPending();

// KPI source — completed handovers in date range, with timestamps loaded.
@Query("""
    SELECT h FROM TicketHandover h
    LEFT JOIN FETCH h.validation v
    LEFT JOIN FETCH v.productionLine
    LEFT JOIN FETCH h.fromTech
    LEFT JOIN FETCH h.toTech
    WHERE  h.scheduledAt BETWEEN :from AND :to
""")
List<TicketHandover> findInRange(@Param("from") LocalDateTime from,
                                 @Param("to")   LocalDateTime to);

// Race-safe transition: returns rows-affected count (must be 1).
@Modifying
@Query("""
    UPDATE TicketHandover h
    SET    h.status = :next, h.toTech = :toTech, h.acceptedAt = :acceptedAt
    WHERE  h.id = :id AND h.status = :expected
""")
int compareAndSetStatus(@Param("id") Long id,
                        @Param("expected") HandoverStatus expected,
                        @Param("next")     HandoverStatus next,
                        @Param("toTech")   User toTech,
                        @Param("acceptedAt") LocalDateTime acceptedAt);
```

### `ValidationRepository` — additional method

```java
@Query("""
    SELECT DISTINCT v FROM Validation v
    LEFT JOIN FETCH v.assignments a
    LEFT JOIN FETCH a.user
    WHERE  v.status = :status
    AND    a.status = 'ACTIVE'
    AND NOT EXISTS (
        SELECT 1 FROM TicketHandover h
        WHERE  h.validation = v
        AND    h.status IN ('PENDING','ACCEPTED')
    )
""")
List<Validation> findEligibleForShiftEndHandover(@Param("status") TicketStatus status);
```

The `NOT EXISTS` clause encodes scheduler idempotency at the
database level (R6).

---

## 8. Relationship diagram (additions only)

```text
Validation (1) ──── (N) TicketHandover ──── (1) User  [fromTech]
                       │
                       └─── (0..1) User      [toTech]

User (1) ─── productionLine ─── (1) ProductionLine
Validation (1) ─── productionLine ─── (1) ProductionLine
                  │
                  └─── used by service-layer zone-locality check (R3)

Validation (1) ──── (N) ValidationAssignment   [+ new handoverNote field]
```

Existing `Notification` entity is reused unchanged. A handover
creates one `Notification` row per personal recipient
(outgoing tech; designated incoming tech) — see
`contracts/handover-websocket-events.md`.

---

## 9. Validation rules (service layer)

| Rule | Where enforced | Maps to spec |
|---|---|---|
| Initiator of manual handover MUST be the active assignee or `ADMIN_IT`. | `HandoverServiceImpl.initiateHandover` | FR-002, FR-016 |
| Force handover MUST be `CHEF_SECTEUR` or `ADMIN_IT`. | `HandoverServiceImpl.initiateHandover` (when `triggerType = ADMIN_FORCE`) | FR-003 |
| Self-accept MUST be `TECH_VAL` AND zone of user MUST equal zone of ticket. | `HandoverServiceImpl.acceptHandover` | FR-019, FR-019a |
| Designated tech MUST currently hold `TECH_VAL` role. | `HandoverServiceImpl.assignHandover` | "recipient no longer eligible" edge case |
| At most one non-terminal `TicketHandover` per validation. | repository `NOT EXISTS` + `compareAndSetStatus` | FR-004, SC-004 |
| At most one `ACTIVE` `ValidationAssignment` per validation. | repository pre-check inside acceptance tx (R1) | FR-007a |
| Frozen-state guard: no result/review/close while `EN_ATTENTE_HANDOVER`, except for original tech (auto-cancel path). | `ValidationService` — call `HandoverService.autoCancelIfPending(...)` first | FR-006, FR-006a |
