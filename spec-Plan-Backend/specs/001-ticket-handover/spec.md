    # Feature Specification: Shift-End Ticket Handover

**Feature Branch**: `001-ticket-handover`
**Created**: 2026-05-05
**Status**: Draft
**Input**: User description: "read plan.md and create a specification for phase 1: specification: feature brief"
**Source plan**: `Plan.md` — Phase 1 (Specification: Feature Brief)

## Overview

At Sagem, the production-line workday ends at 17:00. A validation
technician (`TECH_VAL`) may still be actively working a validation
ticket when their shift closes, leaving the ticket frozen mid-flow with
no one accountable for continuing it. There is currently no formal
mechanism to transfer ownership of an in-progress ticket to another
available technician, and supervisors have no live visibility into
which tickets are stranded.

The **Shift-End Ticket Handover** feature introduces a structured
transfer protocol with three trigger paths (automated, manual, forced),
freezes the ticket in a dedicated waiting state while accountability is
transferred, preserves a full audit trail of every transfer, and feeds
the supervisor dashboard with live queue + KPI metrics.

## Clarifications

### Session 2026-05-05

- Q: When two `TECH_VAL` users click "Accept" on the same pending handover at the same time, who wins? → A: First-commit-wins via a database-level guard (the handover row's `PENDING → COMPLETED` transition is atomic and a ticket may have at most one `ACTIVE` assignment). The loser receives a clear "already accepted by X" error and is returned to the live queue. No silent demotion of the first accepter.
- Q: What happens when the original technician resolves a ticket while a handover is still pending? → A: The pending handover is auto-cancelled transactionally as part of any terminal transition (closure or submit-review) performed by the original technician. The handover record is preserved with status `CANCELLED`, a `HANDOVER_CANCELLED` event is emitted so the live queue panel removes the row, and no supervisor action is required.
- Q: Who is eligible to self-accept a pending handover — any `TECH_VAL`, or only same-zone `TECH_VAL`s? → A: Self-acceptance is restricted to `TECH_VAL` users belonging to the same production line / zone as the ticket. Any cross-zone takeover MUST go through a sector lead or IT admin who explicitly designates the incoming technician via the assign endpoint.
- Q: How are handover alerts persisted when the recipient is offline? → A: Personal alerts (outgoing technician, designated incoming technician) MUST persist a `Notification` row in addition to emitting the live STOMP event, so the recipient sees a bell-icon backlog on next login. Zone-level alerts are live-only — the live queue panel is the system of record for supervisors. No per-supervisor `Notification` row is created for zone events.
- Q: What is the canonical scope of handover KPIs — frequency only, or frequency plus duration metrics? → A: KPIs include count of handovers, median and p95 time-to-accept (derived from `scheduledAt` → `acceptedAt`), and a breakdown by trigger type (automatic / manual / forced). All metrics are sliceable by zone, technician, and caller-supplied date range. This makes SC-003 directly verifiable from the dashboard.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Automated shift-end handover (Priority: P1)

Every weekday at 16:45 the system scans every validation ticket that is
currently being worked on. For each one it freezes the ticket, pauses
the outgoing technician's session, records who was working it and what
was happening, and alerts both the technician and their supervisor
that a transfer is now required before 17:00.

**Why this priority**: This is the core value of the feature — without
the automated sweep, tickets continue to be silently abandoned at
shift end, which is the original problem. It is also what enables the
supervisor's live queue panel and the KPI metric.

**Independent Test**: Stage a ticket in the in-progress state with an
active technician assignment, advance the system clock (or manually
trigger the scheduled job) past 16:45 on a weekday, and verify the
ticket is frozen, the assignment is paused, a handover record exists,
the technician received a personal alert, and the supervisor of the
zone received a queue alert.

**Acceptance Scenarios**:

1. **Given** ticket `VAL-2026-0042` is in progress with technician
   Jean actively assigned at 16:45 on a weekday,
   **When** the shift-end scan runs,
   **Then** the ticket is frozen in the waiting-for-handover state, a
   pending handover is created with trigger type "shift-end automatic",
   Jean's assignment is paused, Jean receives a personal handover
   alert, and the zone's supervisor receives a queue alert.

2. **Given** ticket `VAL-2026-0042` already has a pending handover
   from a previous run of the scan,
   **When** the scan runs again (e.g. a server restart triggers a
   replay),
   **Then** no second handover is created, no duplicate alerts are
   sent, and the existing handover is preserved unchanged.

3. **Given** ticket `VAL-2026-0099` is in the review or closed state at
   16:45,
   **When** the shift-end scan runs,
   **Then** the ticket is ignored — no handover is created and no
   notifications are sent.

---

### User Story 2 — New technician self-accepts a pending handover (Priority: P1)

When a handover is pending, any technician with the right role can
visit the handover page for that ticket, read the previous
technician's progress note and summary, and accept ownership. On
acceptance the ticket immediately resumes as in-progress under the new
owner, and the handover record is closed.

**Why this priority**: Without the accept step, a frozen ticket stays
frozen forever — this story is what makes the feature end-to-end
useful and is the path most validations will follow.

**Independent Test**: Given a pending handover, log in as a different
validation technician, open the handover page for that ticket, click
accept, and verify the ticket returns to in-progress under the new
owner with a new active assignment, the previous progress note is
visible, and the handover record is marked completed with a timestamp.

**Acceptance Scenarios**:

1. **Given** a pending handover exists for ticket `VAL-2026-0042`
   with progress note "QC step 3 of 5 done — defect on track A still
   under inspection",
   **When** technician Mohamed opens the handover page and clicks
   "Accept",
   **Then** the handover is marked completed with an acceptance
   timestamp, the ticket returns to in-progress, a new active
   assignment is created for Mohamed, and Mohamed sees Jean's
   progress note and summary on the accept screen before clicking.

2. **Given** a pending handover for ticket `VAL-2026-0042`,
   **When** Mohamed accepts it,
   **Then** the ticket's history view shows a new entry recording the
   transfer (from Jean to Mohamed, trigger type, timestamp,
   handover note).

---

### User Story 3 — Voluntary manual handover before shift end (Priority: P2)

A technician who knows they will not be able to finish a ticket today
can voluntarily initiate a handover at any moment during their shift,
attaching a progress summary and a handover note. The ticket then
becomes available in the supervisor's queue and on the handover page
for any eligible peer to pick up.

**Why this priority**: Lets diligent technicians close their day
cleanly and gives supervisors earlier visibility than the 16:45 sweep
alone. Same outcome as Story 1 once initiated, so the cost of adding
it on top of P1 is small.

**Independent Test**: Log in as a validation technician with an
in-progress ticket, click "Initiate handover", fill in the progress
summary and note, submit, and verify the same outcome as the automated
flow (ticket frozen, supervisor alerted, queue updated) with the
trigger type recorded as "manual".

**Acceptance Scenarios**:

1. **Given** technician Jean is working ticket `VAL-2026-0055` at
   15:30,
   **When** Jean opens the initiate-handover dialog, fills in a
   progress summary and a handover note, and submits,
   **Then** the ticket is frozen in the waiting-for-handover state, a
   pending handover is created with trigger type "manual", Jean's
   assignment is paused, the supervisor's live queue panel shows the
   ticket immediately, and the personal/zone alerts are sent.

---

### User Story 4 — Supervisor assigns a specific technician (Priority: P2)

When the supervisor wants control over who picks up a stranded
ticket, they can open the live queue panel, pick any pending
handover, and assign it to a specific eligible technician. That
technician receives a personal alert and can accept the handover from
the same accept screen used in Story 2.

**Why this priority**: Required when self-assignment is not the right
distribution mechanism (workload balancing, expertise routing,
absences). Builds on the accept screen from Story 2.

**Independent Test**: Given a pending handover with no designated
recipient, log in as a sector lead, open the live queue panel,
designate technician Sana, verify Sana receives a personal alert,
verify the handover is now marked as assigned, and verify Sana can
accept it from the standard accept screen.

**Acceptance Scenarios**:

1. **Given** a pending handover for ticket `VAL-2026-0042` has no
   designated recipient,
   **When** the supervisor designates technician Sana from the live
   queue panel,
   **Then** the handover records Sana as the recipient, its status
   becomes assigned, Sana receives a personal alert, and Sana can
   accept it from the accept screen.

---

### User Story 5 — Forced handover by IT administrator (Priority: P3)

An IT administrator can force a handover on any in-progress ticket at
any time, regardless of who currently holds it. This is an emergency
override (e.g. a technician is sick mid-shift, account compromise,
production incident) and is recorded distinctly so it can be audited.

**Why this priority**: Important for operational resilience but used
rarely; the feature is still valuable without it.

**Independent Test**: Log in as IT admin, open any in-progress
ticket, force a handover, verify the same outcome as the manual flow
but with trigger type recorded as "admin force" and the original
technician notified.

**Acceptance Scenarios**:

1. **Given** ticket `VAL-2026-0070` is in progress under technician
   Karim,
   **When** an IT administrator forces a handover,
   **Then** the ticket is frozen in the waiting-for-handover state, a
   pending handover is created with trigger type "admin force",
   Karim's assignment is paused with a personal alert explaining the
   override, and the zone supervisor sees the ticket in the live
   queue panel.

---

### User Story 6 — Cancellation of a stale pending handover (Priority: P3)

A pending handover can be cancelled by a supervisor or admin if it is
no longer needed (e.g. the original technician returns and the ticket
is resolved before transfer). Cancellation restores the ticket to its
prior in-progress state under the original technician.

**Why this priority**: Cleanup path. Rare but expected in real
operations; without it, stale handovers pollute the queue and KPIs.

**Independent Test**: Stage a pending handover, log in as supervisor,
cancel it, verify the ticket returns to in-progress under the
original technician, the handover is marked cancelled, and the live
queue panel removes the row.

**Acceptance Scenarios**:

1. **Given** a pending handover exists for ticket `VAL-2026-0042`
   under Jean,
   **When** the supervisor cancels it,
   **Then** the handover is marked cancelled, the ticket returns to
   in-progress, Jean's assignment becomes active again, and the row
   disappears from the live queue panel.

---

### User Story 7 — Handover history & KPI visibility (Priority: P3)

Supervisors, experts, and IT admins can view the full handover
history of any ticket on its detail page, and can see aggregated
handover-frequency metrics by zone, technician, and time period on
the KPI dashboard.

**Why this priority**: Important for the PFE defence and for
long-term operational learning, but not required for day-one
operations.

**Independent Test**: With several completed handovers in the
database, open a ticket detail page and verify a chronological list
of every transfer is visible; open the KPI dashboard, pick a date
range, and verify counts/charts render per zone and per technician.

**Acceptance Scenarios**:

1. **Given** ticket `VAL-2026-0042` has been transferred twice
   (Jean → Sana → Mohamed),
   **When** a supervisor opens the ticket detail page,
   **Then** a timeline shows both transfers in order with the
   from/to technicians, trigger type, timestamp, and handover note.

2. **Given** several handovers occurred over the past 30 days,
   **When** a supervisor opens the KPI dashboard for that range,
   **Then** they see total handover counts and breakdowns by zone
   and by technician.

### Edge Cases

- **No active assignment.** A ticket is in progress but has no
  active technician assignment (data anomaly). The shift-end scan
  MUST skip it and surface it to a supervisor rather than create an
  ownerless handover.
- **Already in waiting-for-handover.** Idempotency — re-running the
  scan, or any caller, MUST NOT create a duplicate pending handover
  for the same ticket.
- **Same technician picks back up.** A technician accepts a handover
  for a ticket they originally held (e.g. they returned after a
  break). Allowed; recorded with from = to so audit history stays
  truthful.
- **Recipient no longer eligible.** A supervisor designates a
  technician who has since lost the validation role. The system
  MUST reject the assignment with a clear message and leave the
  handover pending.
- **Ticket resolved before acceptance.** The original technician
  returns and closes the ticket (or submits it for review) while a
  handover is still pending. The pending handover MUST be
  auto-cancelled as part of the same atomic transition: the handover
  record stays as audit history with status `CANCELLED`, a
  `HANDOVER_CANCELLED` event is emitted to remove the row from the
  live queue panel, and the ticket reaches its terminal state in a
  single user action — no supervisor intervention is required.
- **Holiday or non-working weekday.** The automated scan runs on a
  weekday but the line is not operating. The scan MUST still be
  safe (no false handovers) — with no in-progress tickets it
  produces no records and no alerts, which is acceptable.
- **Clock skew / missed run.** If the scheduler misses 16:45 (server
  was down), tickets remain in their current state. Operators can
  still trigger handovers manually or by force; no retroactive sweep
  is required.
- **Connectivity loss for live alerts.** If a personal recipient
  (outgoing technician, designated incoming technician) is offline
  when the alert fires, the persisted `Notification` row guarantees
  they see the alert in their bell-icon backlog on next login. If a
  supervisor is offline, the live queue panel reflects the pending
  state on their next login — that panel is the system of record at
  zone level. The handover record itself is the durable system of
  record at ticket level.

## Requirements *(mandatory)*

### Functional Requirements

**Triggering**

- **FR-001**: The system MUST scan every weekday at 16:45 for tickets
  currently in progress and trigger a handover for each one that does
  not already have a pending handover.
- **FR-002**: A validation technician MUST be able to voluntarily
  initiate a handover on any ticket they are actively assigned to,
  providing a progress summary and a handover note.
- **FR-003**: A sector lead or IT administrator MUST be able to force
  a handover on any in-progress ticket at any time, regardless of the
  current owner.
- **FR-004**: The system MUST be idempotent: re-running any trigger
  path against a ticket that already has a non-terminal pending
  handover MUST NOT create a duplicate record nor send duplicate
  notifications.

**State transitions**

- **FR-005**: When a handover is initiated, the ticket MUST move
  from in-progress to a dedicated waiting-for-handover state, and the
  outgoing technician's assignment MUST move from active to paused.
- **FR-006**: While a ticket is in the waiting-for-handover state,
  no validation result MAY be submitted, no review MAY be requested,
  and the ticket MAY NOT be closed by anyone other than the original
  technician (see FR-006a).
- **FR-006a**: The original technician (the outgoing tech of the
  pending handover) MAY perform a terminal transition on the ticket
  (closure or submit-review) at any time. Doing so MUST atomically
  auto-cancel the pending handover: the handover record is preserved
  with status `CANCELLED`, a `HANDOVER_CANCELLED` event is emitted,
  and the ticket reaches its terminal state in a single user action.
- **FR-007**: When a handover is accepted, the ticket MUST return to
  in-progress under the accepting technician with a new active
  assignment, and the handover record MUST be marked completed with
  the acceptance timestamp.
- **FR-007a**: Acceptance MUST be atomic and race-safe. At most one
  acceptance per pending handover MAY succeed: the first transition
  from pending to completed wins, and any concurrent acceptance
  attempt MUST fail with an "already accepted" error and return the
  caller to the live queue. The system MUST guarantee that a
  validation ticket has at most one active assignment at any time.
- **FR-008**: When a handover is cancelled, the ticket MUST return to
  its prior in-progress state under the original technician with the
  original assignment reactivated, and the handover record MUST be
  marked cancelled.

**Audit & traceability**

- **FR-009**: Every handover MUST persistently record: the ticket,
  the outgoing technician, the incoming technician (when assigned or
  accepted), the handover note, the progress summary, the trigger
  type (automatic / manual / forced), the creation timestamp, and the
  acceptance timestamp.
- **FR-010**: The ticket detail view MUST display the full
  chronological list of every handover that has occurred on that
  ticket, including from/to technicians, trigger type, timestamp, and
  handover note.
- **FR-011**: A paused assignment MUST remain in the database for
  audit purposes — it is never deleted.

**Notifications & live updates**

- **FR-012**: When a handover is initiated by any path, the outgoing
  technician MUST receive a personal real-time alert.
- **FR-013**: When a handover is initiated by any path, the sector
  lead of the relevant zone MUST receive a real-time alert and the
  ticket MUST appear in their live queue panel.
- **FR-014**: When a sector lead designates a specific incoming
  technician, that technician MUST receive a personal real-time alert
  with a link to the accept screen.
- **FR-015**: The live queue panel MUST update in real time without
  requiring a page refresh as new pending handovers are created and
  as existing ones are accepted, assigned, or cancelled.
- **FR-015a**: Every personal handover alert addressed to a specific
  user (outgoing technician, designated incoming technician) MUST
  persist a `Notification` row in addition to the live event, so a
  recipient who was offline at fire time sees a bell-icon backlog on
  next login. Zone-level alerts are live-only and do not create
  per-supervisor `Notification` rows — the live queue panel is the
  authoritative zone-level view.

**Authorization**

- **FR-016**: Only validation technicians and IT administrators MAY
  manually initiate a handover.
- **FR-017**: Only sector leads and IT administrators MAY designate
  a specific incoming technician for a pending handover.
- **FR-018**: Only sector leads and IT administrators MAY cancel a
  pending handover.
- **FR-019**: Only validation technicians MAY self-accept a pending
  handover; sector leads and IT administrators MAY view but not
  self-accept.
- **FR-019a**: Self-acceptance MUST be restricted to validation
  technicians belonging to the same production line / zone as the
  ticket. A cross-zone technician MAY become the new owner only
  through the supervisor/admin designate-recipient flow (FR-017),
  which records the cross-zone routing in the audit history.
- **FR-020**: KPI handover metrics MUST be visible only to sector
  leads, experts, and IT administrators.

**KPIs**

- **FR-021**: The system MUST expose the following handover KPIs over
  a caller-supplied date range, each sliceable by zone, technician,
  and time period (day, week, month):
  - total count of handovers,
  - median and p95 time-to-accept (creation → acceptance, computed
    only over handovers that reached `COMPLETED`),
  - breakdown of counts by trigger type (automatic / manual / forced).
- **FR-022**: The KPI dashboard MUST recalculate handover metrics
  whenever a handover reaches a terminal state (completed or
  cancelled).

### Key Entities

- **Ticket Handover**: A formal transfer-of-ownership record
  attached to a single validation ticket. Captures who handed off, who
  received, why (trigger type), what was happening at the time
  (progress summary + handover note), when it was created, and when
  it was accepted. Lifecycle: pending → assigned → completed, or
  pending → cancelled.
- **Validation Ticket** *(existing, extended)*: Gains a new
  waiting-for-handover state in its lifecycle, sitting between
  in-progress and review. While in this state, the ticket is frozen.
- **Validation Assignment** *(existing, extended)*: Gains a new
  paused state, used when its technician hands off the ticket so the
  audit history of who held the ticket remains intact even after a
  new active assignment is created.
- **User (Technician / Sector Lead / IT Admin)** *(existing)*: Roles
  determine who can initiate, designate, accept, or cancel a
  handover. A user is associated with a zone, which drives queue
  routing.
- **Zone** *(existing)*: The supervisory grouping used to route
  pending handovers to the right sector lead's live queue.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of tickets that are still in progress at 16:45 on
  a weekday receive a pending handover and a paired pair of
  notifications (technician + sector lead) before 17:00.
- **SC-002**: 0 tickets remain in the in-progress state with no
  active assignment overnight (i.e. no silently abandoned tickets).
- **SC-003**: Median time from handover creation to acceptance is
  under 10 minutes during a working day, demonstrating the live
  queue panel actually drives pickup.
- **SC-004**: Re-running the shift-end scan, restarting the server
  during the 16:45 window, or double-clicking the manual initiate
  button MUST NOT produce duplicate handover records or duplicate
  notifications — verified by automated test on every release.
- **SC-005**: A sector lead can locate every pending handover for
  their zone in under 5 seconds from any screen (single click to the
  live queue panel).
- **SC-006**: For any closed ticket, a supervisor can reconstruct
  the full chain of who held it and for how long in under 30 seconds
  by reading its detail page.
- **SC-007**: The KPI dashboard renders zone- and technician-level
  handover counts for a 30-day range in under 3 seconds.
- **SC-008**: A new validation technician can read the previous
  technician's progress summary and handover note and resume work
  on a transferred ticket without needing to ask the previous
  technician — measured by a "did you need to ask the previous
  technician?" survey item with a target of 80% "no".

## Assumptions

- The existing ticket-validation lifecycle, role model (validation
  technician / preparation technician / sector lead / expert / IT
  admin / responsible), Keycloak-backed authentication, real-time
  notification channel, and KPI dashboard are all in place and will
  be reused — this feature extends them rather than rebuilding them.
- Working hours are a single 08:00–17:00 weekday shift. Night shifts,
  weekend coverage, and overlapping shifts are out of scope.
- The handover window is fixed to 16:45 (15 minutes before 17:00).
  Configurability of this window is not in scope for v1.
- Out of scope (per `Plan.md`): cross-shift scheduling, automatic SLA
  breach escalation beyond notification, reassignment of preparation-
  phase tickets, multi-step approval chains for handover acceptance.
- A technician's "zone" is derivable from their profile and is what
  routes a pending handover to the right sector lead's live queue;
  no new zone-mapping data is required.
- Validation technicians have authenticated, browser-based access
  during their shift; supervisors keep their dashboard open during
  shift hours so live updates are seen in real time.
- French is the language of operator-facing messages (consistent with
  existing UI), but this is a presentation detail and not a
  functional constraint.
