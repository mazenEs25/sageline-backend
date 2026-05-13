# Phase 0 Research: Workflow Guard

All five `/speckit-clarify` questions resolved on 2026-05-12 (see `spec.md` § Clarifications). Remaining unknowns surfaced during plan drafting are resolved below.

## R-001 — Persisting the `mandatory` snapshot on `ValidationMeasure`

**Decision.** Add a non-null boolean column `mandatory_at_creation` to `validation_measures` via `V3.0__validation_measure_mandatory_snapshot.sql`. Backfill existing rows from `poste_measure_catalog.mandatory` via FK join; rows with no catalog template (ad-hoc measures permitted by Phase 002 FR-011) default to `false`. New `ValidationMeasure` rows MUST have the column populated by their writer at insert time — `ValidationMeasureServiceImpl.create*()` reads `catalogTemplate.mandatory` (or `false` for ad-hoc) and stamps it onto the entity once.

**Rationale.** Phase 001 R-005 mandates snapshot semantics; Q1 confirms it. Phase 002 shipped `ValidationMeasure` without the column (verified by reading the entity 2026-05-12). Catching the gap in Phase 003 is cheaper than reading the live catalog and breaking R-005 — and it leaves the Phase 002 contract (no behavioural change for measure CRUD) intact.

**Alternatives considered.**
- *Live join to `poste_measure_catalog.mandatory`.* Rejected: directly violates Q1 + R-005; in-flight tickets would silently change scope when supervisors edit catalog rows.
- *Snapshot in a sibling table.* Rejected: adds JOINs to every readiness query; the field is intrinsic to the measure, not a separable lifecycle event.
- *Backfill column nullable, then tighten in a later phase.* Rejected: the readiness rule treats `null` as a third state, polluting boolean logic. Default-false + immediate backfill is one transaction.

## R-002 — Single STOMP topic vs personal queue for readiness snapshots

**Decision.** Use one shared topic per ticket: `/topic/validation.{id}.readiness`. The existing `SimpMessagingTemplate` infrastructure (Phase 0 broker config in `WebSocketConfig`) handles fanout; subscriber-side access is gated by a `ChannelInterceptor`-equivalent check that mirrors `ValidationService.findById` access (production-line scoping).

**Rationale.** Several roles legitimately watch the same ticket simultaneously (the assigned `TECH_VAL`, a `CHEF_SECTEUR` supervising the line, a `RESPONSABLE` viewing a dashboard). Per-user queues would either (a) require the publisher to know all live subscribers (it doesn't), or (b) push to all roles indiscriminately (defeats access control). One topic + access-checked subscribe is the standard STOMP pattern and is the same shape `HANDOVER_TRIGGERED` already uses for `/topic/handover.zone.{lineId}`.

**Alternatives considered.**
- *Personal queue `/user/{userId}/queue/readiness/{validationId}`.* Rejected: requires the publisher to enumerate watchers; we don't track watcher membership for tickets.
- *Single global topic with `validationId` in payload.* Rejected: every client would receive every snapshot, leaking ticket existence and violating FR-011.

## R-003 — Coverage of `EN_COURS → EN_REVUE` write paths

**Decision.** Wire `TicketTransitionGuard.check(...)` into both:
1. `ValidationService.submitForReview(id)` (L422–L470 in current code) — explicit user submit. On block, throw `TransitionBlockedException` carrying the readiness DTO; mapped to HTTP 422 by `GlobalExceptionHandler`.
2. The poste-closure auto-advance branch (L626–L644) — system-initiated. On block, log a `WARN` with the readiness DTO and **skip** the status mutation (ticket stays `EN_COURS`); do not throw. Rationale: the user is closing a poste, not asking for review; converting that into a 422 would surprise them.

A simple architecture test (using ArchUnit or a hand-rolled JPQL grep) asserts no other code path writes `validation.status = EN_REVUE` outside `TicketTransitionGuard`'s allow branch. SC-006 verified.

**Rationale.** FR-009 / SC-006 demand "no controller, listener, or job can bypass." The auto-advance branch is the one path most likely to be missed; surfacing it here keeps the guard's invariants honest.

**Alternatives considered.**
- *Throw on auto-advance too.* Rejected: cascades a poste-close call into an unrelated 422; user experience degrades for no business benefit.
- *Allow auto-advance to bypass coverage.* Rejected: directly contradicts FR-009.

## R-004 — `WorkflowReadinessService` as the sole producer

**Decision.** A single method `WorkflowReadinessService.computeReadiness(validationId, targetStatus)` returns the fully-populated `WorkflowReadinessDTO`. The REST probe controller calls it. The guard, on block, calls it and stuffs the result into `TransitionBlockedException`. The measure-mutation hook calls it and hands the result to `SimpMessagingTemplate.convertAndSend(...)`. This guarantees byte-equivalence across all three delivery paths and underwrites SC-007.

**Rationale.** Three callers means three opportunities to drift. One producer eliminates that risk and simplifies the integration tests (one method to assert against).

**Alternatives considered.**
- *Compute the DTO in the controller; let the guard use a different shape.* Rejected: SC-007 (probe-says-yes ⇒ submit-succeeds) becomes a per-test invariant rather than a structural one.

## R-005 — `MeasureNotEditableException` (Phase 002) as a parallel pattern

**Decision.** `TransitionBlockedException` mirrors Phase 002's `MeasureNotEditableException` (carrying the offending ticket id) and `BatchMeasureValidationException` (carrying a structured payload). It extends `RuntimeException`, carries the `WorkflowReadinessDTO`, and is mapped in `GlobalExceptionHandler` to `ResponseEntity.status(422).body(ex.getReadiness())`.

**Rationale.** Symmetry with the existing exception family keeps the codebase predictable and lets the `GlobalExceptionHandler` follow the same `@ExceptionHandler` shape it already uses.

## R-006 — Transactional boundary

**Decision.** `submitForReview` runs in the existing `@Transactional` scope of `ValidationService`. The guard's `check()` is *read-only* (no `@Transactional`-write semantics); it runs inside the caller's transaction so its measure reads see any prior in-transaction writes. The STOMP push happens **after** the transaction commits (via `TransactionSynchronizationManager.registerSynchronization` with `afterCommit`) so subscribers never see snapshots that get rolled back.

**Rationale.** Last-writer-wins is established for measure state (Phase 002 Q4); the same model applies here. Post-commit publish is the standard fix for the "subscriber sees ghost data" failure mode.

**Alternatives considered.**
- *Push inside the transaction.* Rejected: a subsequent rollback would leak a phantom snapshot to clients.
- *Add an outbox table.* Rejected: heavy infrastructure for a single-instance app whose STOMP broker is in-process; revisit if/when SageLine scales horizontally.

## R-007 — Readiness probe for tickets not in `EN_COURS`

**Decision.** When a ticket is not in `EN_COURS` (the only legal source for `EN_REVUE`), the probe returns `canTransition=false` with `blockingReasons=["ticket source status <X> is not eligible for transition to EN_REVUE"]` and the coverage counts computed normally (informational). HTTP 200, not an error. This matches the spec Assumptions ("uniform shape regardless of ticket state") and avoids the front end having to special-case status pre-checks.

**Rationale.** The probe is a query, not a transition attempt. Returning a structured "no, because…" payload is more useful than a 4xx.

## R-008 — STOMP topic name format

**Decision.** Topic name is `/topic/validation.{id}.readiness` (dotted segments, matching the existing `/topic/handover.zone.{lineId}` convention from CLAUDE.md). Document in `contracts/workflow-guard-api.openapi.yaml` as a `description` block since OpenAPI 3.0 cannot natively express STOMP topics; AsyncAPI is not adopted in this codebase and adopting it would exceed phase scope.

**Rationale.** Consistency with existing topic naming. Frontend already parses dotted topic names (handover module).
