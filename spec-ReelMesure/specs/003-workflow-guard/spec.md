# Feature Specification: Workflow Guard

**Feature Branch**: `003-workflow-guard`
**Created**: 2026-05-12
**Status**: Draft
**Input**: User description: "Phase 003 — Workflow Guard from plan.md"

## Clarifications

### Session 2026-05-12

- Q: Where does the guard read the `mandatory` flag from — the per-measure snapshot or the live catalog? → A: Read the per-measure snapshot (`ValidationMeasure.mandatoryAtCreation`) captured at ticket creation. Catalog edits made after a ticket is opened do not retroactively change that ticket's mandatory inventory. (Aligns with Phase 001 R-005.)
- Q: Which `MeasureStatus` enum values does Phase 003 reference, and what populates `outOfRangeMeasures`? → A: Use the Phase 002 enum verbatim — `OK`, `OUT_OF_RANGE`, `NOT_EXECUTED` (three values total). "Filled" means `status != NOT_EXECUTED`. The `outOfRangeMeasures` list in the readiness payload contains exactly the measures whose status is `OUT_OF_RANGE`. No new status values are introduced by this phase.
- Q: What HTTP status code does a coverage-blocked submit-for-review return? → A: **HTTP 422 Unprocessable Entity** with the readiness payload as the response body. Matches Plan.md §8 and Phase 002's existing 422 convention for semantic-but-well-formed payload rejection (e.g., `BatchMeasureValidationException`). Authorization failures remain 401/403; wrong-source-status refusals also use 422 with a readiness payload whose `blockingReasons` cite the source-status mismatch.
- Q: Who can call the readiness probe `GET /api/validations/{id}/readiness`? → A: Mirror ticket-read authorization 1:1 — any authenticated user who can read the underlying ticket can read its readiness. The probe inherits whatever ticket-level access check the ticket-detail endpoint already enforces (production-line scoping, etc.). The same Story 3 real-time channel uses the same access rule for subscriptions.
- Q: How deep does the refactor of pre-existing submit-review rules go? → A: **Delegate-and-wrap.** The guard owns sequencing and the uniform refusal-payload shape. Pre-existing checks (role authorization via `@PreAuthorize`, prep-validation rule, original-tech-from-handover allowance per FR-006a) keep their current implementations and are invoked by the guard via thin adapter calls. The new `MandatoryMeasureCoverageRule` is the only fresh rule class introduced by this phase. Genuine extraction of the pre-existing checks into rule classes is deferred until a second transition needs them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Block submit-for-review when mandatory measures are missing (Priority: P1)

A validation technician working an in-progress ticket attempts to advance it to the review stage. The system refuses the transition unless every mandatory measure defined by the ticket's poste-type catalog has been recorded with a real status (i.e., is no longer in the `NOT_EXECUTED` state). When refused, the technician is told exactly which mandatory measures are still missing so they can record them and try again.

**Why this priority**: This is the core business rule of the phase — and the user's headline requirement: "the workflow should not move forward until measurements are real and complete." Without it, technicians can close tickets on empty data, defeating the purpose of the entire validation pipeline. Everything else in this phase (readiness probe, live updates) is a UX wrapper around this rule.

**Independent Test**: Create a ticket whose catalog defines 16 mandatory measures, fill 14 of them with real values, attempt the submit-for-review transition, and confirm the system refuses the transition and identifies the 2 outstanding mandatory measures by code and label.

**Acceptance Scenarios**:

1. **Given** an `EN_COURS` ticket whose zone catalog defines 16 mandatory measures and where 14 measures have a non-`NOT_EXECUTED` status, **When** the technician requests the submit-for-review transition, **Then** the system refuses the transition, leaves the ticket in `EN_COURS`, and returns a structured payload identifying the 2 missing mandatory measures by `measureCode` and label.
2. **Given** the same ticket after the technician records the 2 outstanding measures, **When** the technician requests the submit-for-review transition, **Then** the system performs the transition and the ticket moves to `EN_REVUE`.
3. **Given** an `EN_COURS` ticket whose catalog defines 0 mandatory measures, **When** the technician requests the submit-for-review transition, **Then** the system performs the transition (no mandatory coverage to enforce).

---

### User Story 2 - Inspect transition readiness without attempting the transition (Priority: P2)

Before pressing "submit for review," the technician (or the UI on their behalf) wants to know in advance whether the transition will succeed and, if not, what is blocking it. A read-only readiness probe returns the current count of mandatory measures filled vs. total, the list of missing measures, the list of out-of-range measures, a boolean `canTransition` flag, and human-readable blocking reasons. Calling this probe never changes ticket state.

**Why this priority**: Without this probe the only way to know whether a ticket is submit-ready is to try the transition and read the failure payload — which is fine for the API but produces a poor UX (the button is always live, then errors). With it, the front end can disable the submit button, show a live progress indicator, and explain blockers without provoking failed transitions. It also gives operators and dashboards a cheap, side-effect-free way to audit ticket completeness.

**Independent Test**: For a ticket with 14 of 16 mandatory measures filled, call the readiness probe and confirm it reports `mandatoryFilled=14`, `mandatoryTotal=16`, `canTransition=false`, lists the 2 missing measures, and that calling it twice in a row does not change ticket state or the readiness counts.

**Acceptance Scenarios**:

1. **Given** an `EN_COURS` ticket with 14 of 16 mandatory measures recorded, **When** the readiness probe is called for the default target (next legal status), **Then** the system returns `mandatoryTotal=16`, `mandatoryFilled=14`, `mandatoryMissing=2`, the missing measures by code/label, `canTransition=false`, and at least one blocking reason that names the missing-coverage rule.
2. **Given** the readiness probe is called with an explicit target status of `EN_REVUE`, **When** the response is returned, **Then** the response echoes both the current and the requested target status so the caller can confirm which transition was evaluated.
3. **Given** the readiness probe is called twice in succession on the same ticket, **When** no measures change between calls, **Then** both responses are identical and the ticket's status, audit trail, and stored measures are unchanged.

---

### User Story 3 - Live readiness updates as measures are recorded (Priority: P3)

While the technician is recording measures one by one, the readiness picture should refresh automatically — without the front end having to poll. Every time a measure is created, updated, or deleted on a ticket, the system pushes the latest readiness snapshot for that ticket to subscribers, so any open ticket-detail view sees the progress bar and the submit button's enabled/disabled state update in real time.

**Why this priority**: This is a UX accelerator, not a correctness requirement. Stories 1 and 2 already make the system safe and explorable; this one makes it feel responsive. It is ranked P3 because the contract (the message format and the channel) is what this phase must lock down — the front-end consumer is explicitly out of scope.

**Independent Test**: Subscribe a test client to the per-ticket readiness channel, record one new mandatory measure on that ticket, and confirm exactly one readiness snapshot is pushed to the subscriber whose `mandatoryFilled` count is one higher than before.

**Acceptance Scenarios**:

1. **Given** a subscriber listening on the per-ticket readiness channel, **When** a measure on that ticket is created, updated, or deleted, **Then** the system pushes a single readiness snapshot to that channel reflecting the post-change state.
2. **Given** the change brought the ticket from `canTransition=false` to `canTransition=true` (or vice versa), **When** the snapshot is pushed, **Then** the snapshot's `canTransition` flag reflects the new value so the subscriber can re-render the submit button.
3. **Given** a measure change on ticket A, **When** the snapshot is pushed, **Then** subscribers listening on other tickets' channels do not receive a snapshot for ticket A.

---

### Edge Cases

- **Ticket not in `EN_COURS`**: A request to transition a ticket whose current status is not `EN_COURS` is refused as an illegal transition, regardless of measure coverage. The blocking reason cites the wrong-source-status, not the coverage rule, so the technician is not misled into recording more measures.
- **Catalog has zero mandatory templates**: Coverage is vacuously satisfied; the transition proceeds. Readiness reports `mandatoryTotal=0`, `mandatoryFilled=0`, `canTransition=true`.
- **Out-of-range measures present but all mandatory measures filled**: Measures with `status=OUT_OF_RANGE` are surfaced in the readiness payload's `outOfRangeMeasures` list for visibility, but they do **not** block the transition in this phase — closure verdict (conform / non-conform) is Phase 005's responsibility. `canTransition` reflects coverage only.
- **Mandatory measure recorded then later deleted**: The readiness recount drops `mandatoryFilled` by one; if the ticket was already `EN_REVUE` the deletion is rejected by the data-mutation guard introduced in Phase 002 (measures only editable in `EN_COURS`), so the readiness regression cannot occur post-transition.
- **Concurrent submit attempts**: Two technicians press submit at nearly the same instant on the same ticket. The second request finds the ticket already in `EN_REVUE` and is refused with a wrong-source-status blocking reason; no double-transition occurs.
- **Readiness probe called on a ticket the caller cannot access**: The probe respects the same access rules as reading the ticket itself. An unauthorized caller receives the same access denial they would get from reading the ticket; no readiness data is leaked.
- **Bulk measure import (Phase 004 forward-look)**: A single import that creates many measures at once should not produce a flood of one snapshot per measure. The contract permits coalescing into a single snapshot per ticket per import operation; consumers must be tolerant of either behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST refuse the `EN_COURS → EN_REVUE` transition for a ticket whenever any mandatory measure defined by that ticket's zone catalog has a status of `NOT_EXECUTED`.
- **FR-002**: System MUST permit the `EN_COURS → EN_REVUE` transition only when every mandatory measure defined by the ticket's zone catalog has a status other than `NOT_EXECUTED` (i.e., `OK` or `OUT_OF_RANGE`).
- **FR-003**: System MUST refuse a submit-for-review request whose source ticket is in any status other than `EN_COURS`, citing the wrong source status as the blocking reason rather than measure coverage.
- **FR-004**: When refusing a submit-for-review request, the system MUST return a structured readiness payload that names every blocking reason and lists every missing mandatory measure by `measureCode` and label so the caller can act without further round-trips.
- **FR-005**: System MUST expose a non-mutating readiness probe that returns the same readiness payload format used in refusal responses. The probe MUST mirror the ticket-detail endpoint's authorization 1:1 — any authenticated user who can read the underlying ticket can read its readiness, and the same access rule MUST gate subscriptions to the per-ticket readiness real-time channel.
- **FR-006**: The readiness probe MUST accept an optional explicit target status and, when omitted, MUST evaluate readiness for the next legal status from the ticket's current status (default target: `EN_REVUE` for `EN_COURS` tickets).
- **FR-007**: The readiness payload MUST include, at minimum: ticket identifier, current status, evaluated target status, mandatory total, mandatory filled, mandatory missing count, the list of missing mandatory measures, the list of out-of-range measures, a boolean `canTransition` flag, and a list of human-readable blocking reasons.
- **FR-008**: Measures whose status is `OUT_OF_RANGE` MUST be reported in the readiness payload's `outOfRangeMeasures` list but MUST NOT, in this phase, block the `EN_COURS → EN_REVUE` transition; `canTransition` reflects mandatory coverage only.
- **FR-009**: All transition validation logic MUST funnel through a single guard service such that no controller, listener, or job can bypass the rules; new transition rules added in future phases plug into the same guard.
- **FR-010**: Every successful create, update, or delete of a measure on a ticket MUST cause a readiness snapshot for that ticket to be published to a per-ticket real-time channel scoped to that ticket id.
- **FR-011**: Readiness snapshots published to a per-ticket channel MUST reach only subscribers of that ticket's channel; snapshots MUST NOT cross-publish to other tickets' channels.
- **FR-012**: The readiness probe MUST NOT mutate ticket state, measure state, or audit fields; calling it any number of times MUST be observably indistinguishable from not calling it.
- **FR-013**: Refusal responses for a blocked submit-for-review MUST return **HTTP 422 Unprocessable Entity** with the structured readiness payload as the response body. Authorization failures MUST remain 401/403 and MUST NOT be conflated with 422 coverage refusals, so the front end can route 422 responses into a readiness-aware error display.
- **FR-014**: The readiness payload's mandatory counts MUST be derived from the per-measure snapshot field captured at ticket creation (`ValidationMeasure.mandatoryAtCreation`), not from a live read of the catalog. Catalog edits applied after a ticket is opened MUST NOT alter that ticket's mandatory inventory or readiness counts.
- **FR-015**: Existing transition checks already enforced on submit-for-review (role authorization via `@PreAuthorize`, prep-validation rule, original-tech-from-handover allowance per FR-006a) MUST continue to apply and MUST be invoked through the single guard via thin adapter calls so the order of evaluation and the format of refusals are uniform. Their existing implementations are not rewritten in this phase; only the new mandatory-measure-coverage rule is introduced as a fresh rule class. Future phases that need to plug additional transitions into the same guard MAY extract the legacy checks into full `TransitionRule` classes at that time.

### Key Entities *(include if feature involves data)*

- **Workflow Readiness Snapshot**: A read-only computed view of a single ticket at a single moment in time. Carries the ticket's current status, the target status being evaluated, mandatory coverage counts, the list of missing mandatory measures, the list of out-of-range measures, the `canTransition` verdict, and the list of blocking reasons. Not persisted; recomputed on demand and on every measure mutation.
- **Transition Rule**: A named, independently testable business rule that, given a ticket and a candidate target status, returns either "allow" or "block with reason(s)." The mandatory-measure-coverage rule is the rule introduced by this phase; existing role and prep rules are restated as rules of the same shape so they live behind the same guard.
- **Transition Guard**: The single entry point that sequences transition rules for a given source-target pair, aggregates their verdicts, and returns either an allow signal (with the transition free to proceed) or a block signal (with a readiness payload). Also the producer of readiness snapshots, ensuring the readiness probe and the refusal payload are computed by the same code path.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submit-for-review requests against tickets with at least one mandatory measure still in `NOT_EXECUTED` are refused with a readiness payload identifying every missing measure — verified across the integration suite that this phase ships.
- **SC-002**: 100% of submit-for-review requests against tickets with full mandatory coverage are accepted (assuming all other existing rules pass) — verified by the happy-path integration test.
- **SC-003**: A readiness probe call on a ticket with up to 100 measures returns in under 300 ms at the 95th percentile, matching the read-latency budget set for measure listings in Phase 002.
- **SC-004**: Calling the readiness probe an arbitrary number of times leaves the ticket's persisted state byte-for-byte unchanged — verified by an integration test that snapshots ticket state before and after N probe calls.
- **SC-005**: After any measure create/update/delete on a ticket, a readiness snapshot for exactly that ticket is published on the per-ticket channel within 500 ms at the 95th percentile, observed by a contract test that subscribes to the channel.
- **SC-006**: Zero submit-for-review transitions can occur without first passing the guard — verified by a code-review checklist item plus an integration test that asserts every controller path leading to `EN_REVUE` invokes the guard.
- **SC-007**: When the readiness payload reports `canTransition=true`, the immediately following submit-for-review call succeeds 100% of the time in the absence of intervening measure changes — verified by a contract test.
- **SC-008**: When a blocked submit-for-review is followed by recording the missing measures and a retry, the retry succeeds without any change to the guard configuration — verified by an end-to-end integration test that walks the failure → fix → success cycle.

## Assumptions

- The ticket's mandatory-measure inventory is fully derivable from the per-measure `mandatoryAtCreation` snapshot persisted on each `ValidationMeasure` row by the catalog-instantiation operation introduced in Phase 002 (per Phase 001 R-005). No live catalog query is performed by the guard, and no separate per-ticket override of "mandatory" exists in this phase.
- "Mandatory measure has been recorded" is operationally equivalent to "the measure exists for the ticket with a status other than `NOT_EXECUTED`," using the three-valued `MeasureStatus` introduced by Phase 002 — no new status value is needed.
- Out-of-range coverage gating (i.e., refusing review when `KO` measures exist) belongs to closure-verdict logic and is deferred to Phase 005; this phase only surfaces `KO`/`OUT_OF_TOLERANCE` measures informationally.
- Other transitions (`PREP_VALIDEE → EN_COURS`, closure transitions, cancellation, handover transitions) keep their current validation rules untouched in this phase; the new guard architecture is shaped to receive them in future phases without rework.
- Real-time readiness snapshots are delivered over the existing in-app real-time messaging channel that already serves notifications and handover events; no new transport layer is introduced.
- The default target status of the readiness probe (when no explicit target is supplied) is `EN_REVUE` for `EN_COURS` tickets; for tickets in any other status the default behavior is to return a payload with `canTransition=false` and a blocking reason citing the source-status mismatch — this gives the front end a single uniform shape regardless of ticket state.
- Concurrency model continues to follow last-writer-wins as established in Phase 002; the guard reads measure state at evaluation time and does not introduce optimistic locking.
- Bulk measure mutations (e.g., the future log-import flow in Phase 004) are permitted to coalesce many readiness pushes into a single push per ticket per operation; consumers must tolerate either fine-grained or coalesced delivery.
- The frontend deliverables described in `Plan.md` §8 (progress bar component, button wiring, toast on 422) are explicitly out of scope for this phase; this spec defines only the backend contract those frontend pieces will consume.
