# Phase 0 — Research & Decisions

**Feature**: Shift-End Ticket Handover (`001-ticket-handover`)
**Date**: 2026-05-05
**Status**: complete — no `NEEDS CLARIFICATION` items remain.

This document records the design decisions taken before
data-model and contract work, and the alternatives rejected. Five
of the entries map 1-to-1 onto the questions resolved in
`spec.md` §Clarifications; the rest are best-practice decisions
forced by the existing SageLine architecture.

---

## R1. Atomicity & race-safety of acceptance

**Decision.** First-commit-wins, enforced by **two complementary
guards**:

1. **JPA optimistic lock on `TicketHandover.status`.** The
   `PENDING → COMPLETED` transition is a single `UPDATE … WHERE
   id = ? AND status = 'PENDING'`; if zero rows are affected, the
   service throws `ValidationException("Handover already
   accepted")`. This is the primary guard and works without any
   custom DB index.
2. **Service-layer pre-check that the validation has no other
   `ACTIVE` assignment.** Before creating the new
   `ValidationAssignment(status=ACTIVE)`, the service queries by
   `(validation_id, status='ACTIVE')`. The whole acceptance is
   `@Transactional`, so the read+write happens inside one tx — at
   transaction isolation `READ_COMMITTED` (Postgres default), this
   plus the optimistic-lock guard above is sufficient: the second
   tx will fail on the handover-status update and roll back before
   inserting an assignment.

**Rationale.** Avoids a custom partial unique index (which JPA
`ddl-auto=update` cannot manage) while keeping the invariant "at
most one `ACTIVE` assignment per ticket". The optimistic lock is
the single point that serializes acceptance — the assignment
insert is downstream of it inside the same transaction.

**Alternatives considered.**
- Postgres partial unique index `(validation_id) WHERE status =
  'ACTIVE'`. Rejected: requires manual SQL outside the JPA model,
  conflicts with `ddl-auto=update` workflow, and the optimistic
  lock already serializes correctly.
- `SELECT … FOR UPDATE` on the handover row. Rejected: works but
  adds row-level locking overhead with no extra correctness over
  the status-conditional UPDATE.
- `@Version` column on `TicketHandover`. Rejected: equivalent
  semantics, but adds a DB column where a status guard already
  expresses the same intent more readably.

**Maps to.** Spec FR-007a; SC-004.

---

## R2. Atomic auto-cancel on early closure

**Decision.** Inside the existing `ValidationService` close /
submit-review methods, before changing ticket status to a
terminal value, **call `HandoverService.autoCancelIfPending(ticket)`**.
That method:

1. Finds any `TicketHandover` for the ticket where `status IN
   (PENDING, ACCEPTED)`.
2. Marks it `CANCELLED`.
3. Re-activates the original technician's `PAUSED` assignment
   (sets `status=ACTIVE`, leaves `handoverNote` intact).
4. Emits a `HandoverNotificationDto(type="HANDOVER_CANCELLED")`
   on `/topic/handover.zone.{zoneId}` so the queue panel removes
   the row.

The whole sequence is part of the calling `@Transactional` boundary
— either everything commits or nothing does.

**Rationale.** Encodes spec FR-006a as a single service hook
called from the existing closure flow. Keeps `ValidationService`
unaware of `TicketHandover` internals (just calls the
auto-cancel API). Atomic with the closure transition by
construction.

**Alternatives considered.**
- A JPA entity listener (`@PreUpdate`) on `Validation`. Rejected:
  hidden side-effects are the kind of magic the constitution
  Principle III explicitly forbids ("transitions go through
  service methods").
- Block closure while a handover is pending and force the user to
  cancel first. Rejected by `/speckit-clarify` Q2 (Option B).

**Maps to.** Spec FR-006a; Q2 in `spec.md` §Clarifications.

---

## R3. Zone-locality of self-acceptance

**Decision.** Enforced at the **service layer**, not at
`@PreAuthorize`:

```text
acceptHandover(handoverId):
   currentUser = SecurityUtils.getCurrentUserId()
   handover    = handoverRepo.findById(handoverId)
   ticketZone  = handover.validation.productionLine
   userZone    = currentUser.productionLine
   if !ticketZone.equals(userZone):
       throw ValidationException("Cross-zone self-accept not allowed")
```

`@PreAuthorize("hasRole('TECH_VAL')")` covers the role check;
zone equality is a domain-level check.

**Rationale.** Role checks belong in `@PreAuthorize`; data-driven
checks (does this user belong to this ticket's zone?) belong in
service code where they can access loaded entities. Mirrors the
existing `User → ProductionLine` association already used by the
rest of the system.

**Alternatives considered.**
- Custom Spring Security expression `@hasZone(...)`. Rejected:
  overengineered for a one-call use; harder to test; couples
  Security layer to domain.
- Filter the list of pending handovers shown to a `TECH_VAL` so
  cross-zone tickets are simply invisible. Rejected: works for
  the UI but doesn't protect the API endpoint against direct
  calls.

**Maps to.** Spec FR-019a; Q3 in `spec.md` §Clarifications.

---

## R4. Notification persistence policy

**Decision.** The existing `NotificationService` already creates
`Notification` rows and pushes via `SimpMessagingTemplate`. For
handovers:

| Audience | Personal `Notification` row? | STOMP topic |
|---|---|---|
| Outgoing `TECH_VAL` | YES | `/user/{userId}/queue/handover` |
| Designated incoming `TECH_VAL` | YES (created when assign / accept-by-supervisor fires) | `/user/{userId}/queue/handover` |
| Zone supervisors | NO | `/topic/handover.zone.{zoneId}` |

**Rationale.** Personal alerts must survive offline recipients
(SC-008 + spec FR-015a). Zone supervisors keep the dashboard open
during shift hours; the live queue panel is the system of record
at zone scope, so per-supervisor `Notification` rows would just
add noise.

**Alternatives considered.**
- Persist for everyone (Q4 Option B). Rejected: bell-icon noise
  for supervisors who already see the live panel.
- Persist for nobody (Q4 Option C). Rejected: a `TECH_VAL` who
  was offline at 16:45 would have no breadcrumb on next login.

**Maps to.** Spec FR-015a; Q4 in `spec.md` §Clarifications.

---

## R5. KPI computation & exposure

**Decision.** Add to `KPIService` (or a new sibling
`HandoverKpiService` collaborator) the following derivations:

- **Count of handovers**, grouped by zone / technician / period.
- **Median + p95 time-to-accept** = `acceptedAt − scheduledAt`,
  computed only over `status = COMPLETED` rows. Computed in Java
  from the loaded list (Postgres `percentile_cont` is also
  acceptable but Java keeps the JPQL portable and the dataset
  small).
- **Count by trigger type** (`MANUAL`, `SHIFT_END_AUTO`,
  `ADMIN_FORCE`).

Exposed at `GET /api/handovers/kpis?from=YYYY-MM-DD&to=YYYY-MM-DD`,
returning a `HandoverKpiResponse` DTO. Recomputed lazily on
request — there is no separate KPI snapshot table for handovers.

**Rationale.** Volume is small (≤10 handovers/day × 30 days = 300
rows for a monthly slice). On-demand computation is simpler and
cheaper than maintaining a denormalized cache. Median + p95 in
Java is straightforward over a `List<Duration>`.

**Alternatives considered.**
- Frequency only (Q5 Option B). Rejected — leaves SC-003
  unverifiable from the dashboard.
- Pre-aggregated KPI snapshot table updated on each terminal
  transition. Rejected: premature optimization at this scale.

**Maps to.** Spec FR-021, FR-022; SC-003, SC-007; Q5 in `spec.md`
§Clarifications.

---

## R6. Scheduler idempotency strategy

**Decision.** `ShiftEndHandoverJob` runs a single JPQL query that
returns only validations *eligible* for an automated handover:

```sql
SELECT DISTINCT v
FROM   Validation v
JOIN FETCH v.assignments a
JOIN FETCH a.user
WHERE  v.status = 'EN_COURS'
AND    a.status = 'ACTIVE'
AND NOT EXISTS (
    SELECT 1 FROM TicketHandover h
    WHERE h.validation = v
    AND   h.status IN ('PENDING','ACCEPTED')
)
```

This pushes idempotency to the database — the job cannot create
a duplicate handover even on a server restart that re-fires the
trigger or under a manual replay. The cron expression
`0 45 16 * * MON-FRI` is registered via `@Scheduled` on a single
component bean (`SagelineApplication` carries `@EnableScheduling`).

**Rationale.** Database-level filter is more robust than a Java
`if (!ticket.hasNoPendingHandover())` check since the latter is
race-prone if two scheduler nodes ever fire concurrently (not
expected today, but cheap insurance).

**Alternatives considered.**
- Java-side `hasNoPendingHandover` flag derived from the
  collection. Rejected: simpler but vulnerable to dual-instance
  scheduler firing (Plan.md leaves room for a future cluster).
- A `ShedLock` library to serialize cluster execution. Deferred —
  single-node deployment today; if the project ever scales out,
  reintroduce.

**Maps to.** Spec FR-001, FR-004, US 1 scenario 2 (idempotency).

---

## R7. WebSocket topic conventions

**Decision.** Use the conventions already established in
`WebSocketConfig` (broker `/ws`, app prefix `/app`, topic prefix
`/topic`). Extend with two new destinations:

- **Personal**: `/user/{userId}/queue/handover` —
  `SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/handover", payload)`.
- **Zone**: `/topic/handover.zone.{zoneId}` —
  `SimpMessagingTemplate.convertAndSend("/topic/handover.zone." + zoneId, payload)`.

Payload is always `HandoverNotificationDto` (typed). Event types
on the DTO: `HANDOVER_TRIGGERED`, `HANDOVER_ASSIGNED`,
`HANDOVER_ACCEPTED`, `HANDOVER_CANCELLED`.

**Rationale.** Constitution Principle IV mandates typed payloads
on canonical topic prefixes. Reusing existing infrastructure
(`/ws` STOMP endpoint, JWT-aware connect handshake from
`WebSocketEventListener`) avoids new auth wiring.

**Alternatives considered.**
- A single `/topic/handover` global firehose. Rejected: leaks
  cross-zone events to every supervisor.
- Server-Sent Events. Rejected: project already standardized on
  STOMP/WebSocket; switching protocols mid-feature is unjustified.

**Maps to.** Spec FR-012, FR-013, FR-014, FR-015.

---

## R8. Schema migration approach

**Decision.** Rely on `spring.jpa.hibernate.ddl-auto=update` to:

- Add `EN_ATTENTE_HANDOVER` to the `ticket_status` column's
  allowed enum values (because `@Enumerated(EnumType.STRING)`
  stores it as a `varchar`, this is a no-op at DDL level — only
  the Java enum changes).
- Add `PAUSED` to `assignment_status` similarly.
- Add a new `handover_note text` column to
  `validation_assignments`.
- Create the `ticket_handovers` table via JPA mapping.

**No manual SQL migration is required.** Existing rows have
their old status values and remain valid.

**Rationale.** Matches the constitution's "ddl-auto=update is
authoritative" stance. Adding enum values stored as varchar is
backwards-compatible. New table + new column are pure additions.

**Alternatives considered.**
- Flyway / Liquibase. Rejected for v1 — the project has not
  adopted a migration tool yet, and introducing one is a
  cross-cutting concern out of scope here.

**Maps to.** Constitution §Technology Stack; spec data-model
section.

---

## R9. Testing strategy

**Decision.** Default-skip is preserved (pom config), but the
following are added and runnable via `./mvnw test
-DskipTests=false`:

- **Unit tests** for `HandoverServiceImpl` covering all six
  operations (initiate, autoTrigger, accept, assign, cancel,
  autoCancelIfPending) including the race-safety branch (R1) and
  zone-locality branch (R3).
- **Slice test** for `ShiftEndHandoverJob` using
  `@DataJpaTest` + manual call to the job method (cron is not
  triggered in tests).
- **Web slice test** (`@WebMvcTest`) for `HandoverController`
  with mocked service to verify role gating
  (`@PreAuthorize`) for each endpoint.

Integration tests using a real Postgres are deferred (existing
project doesn't ship them).

**Rationale.** Provides verification of the critical
correctness invariants (race, idempotency, zone-locality, role
gating) without requiring a Postgres testcontainer.

**Maps to.** Constitution §Development Workflow item 5.

---

## R10. Frontend interaction contract (for backend completeness)

**Decision.** Backend MUST publish:

1. The 7 REST endpoints in `contracts/handover-rest-api.md`.
2. The 4 WebSocket event payloads in
   `contracts/handover-websocket-events.md`.

No frontend code is produced by this plan; the Angular
implementation (`Plan.md` §Phase 4) consumes these contracts.

**Maps to.** Plan.md §Phase 3 + §Phase 4 boundary.

---

## Open items deferred (NOT blocking implementation)

- **i18n / French copy of notification messages.** Strings are
  defined inline at service-layer for v1; extraction to a
  resource bundle deferred.
- **Cluster-safe scheduler** (multi-instance deployment). Single
  node today; if cluster mode becomes real, add ShedLock per R6.
- **Manual SQL migration tooling** (R8). Reconsider when the
  project formally adopts Flyway.
