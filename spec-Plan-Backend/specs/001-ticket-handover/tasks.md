---
description: "Task list for Shift-End Ticket Handover (001-ticket-handover)"
---

# Tasks: Shift-End Ticket Handover

**Input**: Design documents from `/specs/001-ticket-handover/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/handover-rest-api.md`, `contracts/handover-websocket-events.md`, `quickstart.md`
**Tests**: Test tasks ARE included — `research.md` §R9 committed to a unit + slice + web-slice test strategy.

**Organization**: Tasks are grouped by user story (US1–US7 from `spec.md`) so each story can be implemented and demoed independently. Foundational tasks (entity, enums, repository, mapper, controller skeleton, security) are unavoidable shared prerequisites — see Phase 2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story label (US1–US7); omitted for Setup, Foundational, and Polish.
- All paths are repo-relative under the SageLine backend root unless stated.

## Path Conventions

- Backend root: `src/main/java/com/pfe/sageline/`
- Tests root: `src/test/java/com/pfe/sageline/`
- Frontend (out of scope for this backend tasks file): see `Plan.md` §Phase 4.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Enable the cron infrastructure the scheduler requires. The rest of the project bootstrap (Maven, Spring Boot, Postgres, Keycloak, WebSocket) already exists.

- [X] T001 Enable Spring scheduling: add `@EnableScheduling` to `src/main/java/com/pfe/sageline/SagelineApplication.java`.
- [X] T002 [P] Add handover endpoint URL rules to `src/main/java/com/pfe/sageline/Config/SecurityConfig.java` mirroring the role table in `contracts/handover-rest-api.md` §"Authorization summary".

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: All seven user stories need the entity, enums, repository, mapper, controller skeleton, and notification typing in place. No story can ship until this phase is green.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Enums

- [X] T003 [P] Create `HandoverStatus` enum (`PENDING`, `ACCEPTED`, `COMPLETED`, `CANCELLED`) in `src/main/java/com/pfe/sageline/enums/HandoverStatus.java` per `data-model.md` §2.
- [X] T004 [P] Create `TriggerType` enum (`MANUAL`, `SHIFT_END_AUTO`, `ADMIN_FORCE`) in `src/main/java/com/pfe/sageline/enums/TriggerType.java` per `data-model.md` §3.
- [X] T005 [P] Add `EN_ATTENTE_HANDOVER` value to `src/main/java/com/pfe/sageline/enums/TicketStatus.java` per `data-model.md` §4.
- [X] T006 [P] Add `PAUSED` value to `src/main/java/com/pfe/sageline/enums/AssignmentStatus.java` per `data-model.md` §5.

### Entity & repository

- [X] T007 Create `TicketHandover` JPA entity in `src/main/java/com/pfe/sageline/entity/TicketHandover.java` with the schema in `data-model.md` §1 (Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder`, lazy `@ManyToOne` to `Validation`/`User`, `@Enumerated(STRING)` for both enums). Depends on T003, T004.
- [X] T008 Add `handoverNote` TEXT field to `src/main/java/com/pfe/sageline/entity/ValidationAssignment.java` per `data-model.md` §6.
- [X] T009 Create `HandoverRepository` in `src/main/java/com/pfe/sageline/repository/HandoverRepository.java` with the five queries in `data-model.md` §7 (`findActiveByValidation`, `findByValidationOrderByScheduledAtAsc`, `findAllPending`, `findInRange`, `compareAndSetStatus`). Depends on T007.
- [X] T010 Add `findEligibleForShiftEndHandover` to `src/main/java/com/pfe/sageline/repository/ValidationRepository.java` per `data-model.md` §7 (the `NOT EXISTS` query enforcing scheduler idempotency at DB level — `research.md` R6). Depends on T007.

### DTOs & mapper

- [X] T011 [P] Create `HandoverInitiateRequest` record in `src/main/java/com/pfe/sageline/dtos/request/HandoverInitiateRequest.java` (`handoverNote`, `progressSummary`).
- [X] T012 [P] Create `HandoverAssignRequest` record in `src/main/java/com/pfe/sageline/dtos/request/HandoverAssignRequest.java` (`techId`).
- [X] T013 [P] Create `HandoverResponse` record in `src/main/java/com/pfe/sageline/dtos/response/HandoverResponse.java` per `contracts/handover-rest-api.md` §DTOs.
- [X] T014 [P] Create `HandoverKpiResponse` record (with nested `ZoneCount`, `TechnicianCount`, `TimeToAcceptStats`) in `src/main/java/com/pfe/sageline/dtos/response/HandoverKpiResponse.java`.
- [X] T015 Create `HandoverMapper` in `src/main/java/com/pfe/sageline/mappers/HandoverMapper.java` — `toResponse(TicketHandover)` returning `HandoverResponse`, dereferencing `validation.ticketCode` and `fromTech/toTech.username`. Depends on T007, T013.

### WebSocket payload type

- [X] T016 [P] Create `HandoverNotificationDto` record in `src/main/java/com/pfe/sageline/dtos/response/HandoverNotificationDto.java` (`type`, `handoverId`, `validationId`, `ticketCode`, `message`, `timestamp`) per `contracts/handover-websocket-events.md` §2.

### Service contract

- [X] T017 Create `HandoverService` interface in `src/main/java/com/pfe/sageline/service/HandoverService.java` declaring all six operations (`initiateHandover`, `triggerAutoHandover`, `acceptHandover`, `assignHandover`, `cancelHandover`, `getHandoverHistory`, `getHandoverKpis`, `autoCancelIfPending`) per `Plan.md` §"HandoverService — Operation Contract" plus `research.md` R2.
- [X] T018 Create empty `HandoverServiceImpl` skeleton in `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java` — Lombok `@RequiredArgsConstructor`, `@Service`, `@Transactional`, dependencies (`HandoverRepository`, `ValidationRepository`, `ValidationAssignmentRepository`, `UserRepository`, `NotificationService`, `SimpMessagingTemplate`, `SecurityUtils`, `HandoverMapper`). Methods throw `UnsupportedOperationException` until their owning user story implements them. Depends on T009, T015, T016, T017.

### Controller skeleton

- [X] T019 Create `HandoverController` skeleton in `src/main/java/com/pfe/sageline/controller/HandoverController.java` at base path `/api/handovers`, with the seven endpoint signatures from `contracts/handover-rest-api.md` §"Authorization summary" and matching `@PreAuthorize` annotations. Method bodies delegate to `HandoverService` (currently throws). Depends on T017.

### Notification persistence helper

- [X] T020 Add helper `NotificationService.createForUser(userId, type, message, link)` (or extend the existing API) so handover service methods can persist a personal `Notification` row before STOMP emission per `research.md` R4 / FR-015a. Path: `src/main/java/com/pfe/sageline/service/NotificationService.java` (modify if present, or its impl).

**Checkpoint**: Foundation ready — every user story can now be implemented and tested independently.

---

## Phase 3: User Story 1 — Automated shift-end handover (Priority: P1) 🎯 MVP

**Goal**: Every weekday at 16:45 the system sweeps all in-progress tickets, freezes each one, pauses its outgoing technician, persists a `PENDING` `TicketHandover` with trigger `SHIFT_END_AUTO`, and emits the personal + zone alerts. Idempotent across re-runs.

**Independent Test**: Stage a ticket in `EN_COURS` with one `ACTIVE` assignment, manually invoke `ShiftEndHandoverJob.triggerShiftEndHandovers()`, and verify the post-conditions. Re-invoke and verify NO duplicate side effects.

**Maps to**: FR-001, FR-004, FR-005, FR-009, FR-011, FR-012, FR-013, FR-015a; SC-001, SC-004; spec §"Acceptance Scenarios" 1, 2, 3 of US 1.

### Tests for US1

- [ ] T021 [P] [US1] Slice test for `ShiftEndHandoverJob`: `src/test/java/com/pfe/sageline/scheduler/ShiftEndHandoverJobTest.java`. Use `@DataJpaTest` (or `@SpringBootTest` with `@MockBean` for STOMP), seed an `EN_COURS` validation + `ACTIVE` assignment, call the job method, assert: ticket `EN_ATTENTE_HANDOVER`, assignment `PAUSED`, exactly one `TicketHandover(status=PENDING, triggeredBy=SHIFT_END_AUTO)`. Re-invoke and assert no duplicate row.
- [X] T022 [P] [US1] Unit test for `HandoverServiceImpl.triggerAutoHandover(Validation)`: `src/test/java/com/pfe/sageline/service/HandoverServiceImplAutoTriggerTest.java` covering the happy path and the "already pending" no-op path.

### Implementation for US1

- [X] T023 [US1] Implement `HandoverServiceImpl.triggerAutoHandover(Validation ticket)`: create `TicketHandover(status=PENDING, triggeredBy=SHIFT_END_AUTO, fromTech=activeAssignment.user, scheduledAt=now)`, set ticket → `EN_ATTENTE_HANDOVER`, set assignment → `PAUSED`. Path: `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java`. Depends on T018.
- [X] T024 [US1] In the same method, after the DB write commits, persist a personal `Notification` for the outgoing tech (via T020) and emit STOMP `HANDOVER_TRIGGERED` to `/user/{outgoingTechId}/queue/handover` and `/topic/handover.zone.{zoneId}` with French copy from `contracts/handover-websocket-events.md` §4. Use `TransactionSynchronizationManager.registerSynchronization` `afterCommit` per `contracts/handover-websocket-events.md` §6.
- [X] T025 [US1] Create `ShiftEndHandoverJob` in `src/main/java/com/pfe/sageline/scheduler/ShiftEndHandoverJob.java` with `@Component`, `@RequiredArgsConstructor`, single method annotated `@Scheduled(cron = "0 45 16 * * MON-FRI")` calling `validationRepository.findEligibleForShiftEndHandover(EN_COURS)` and looping `handoverService.triggerAutoHandover(...)`. Depends on T010, T023.
- [ ] T026 [US1] Verify quickstart §3: stage data, manually invoke the job, observe all post-conditions and a re-invocation idempotency check.

**Checkpoint**: US1 fully functional — automated 16:45 sweep with idempotent behaviour and live alerts.

---

## Phase 4: User Story 2 — New technician self-accepts a pending handover (Priority: P1)

**Goal**: A `TECH_VAL` in the same zone as the ticket can accept a `PENDING` (or `ACCEPTED`-to-them) handover; the ticket resumes under the new owner, the handover is `COMPLETED`, the supervisor's queue panel removes the row.

**Independent Test**: With a `PENDING` handover staged by US1 (or manual flow), call `POST /api/handovers/{id}/accept` as a same-zone `TECH_VAL`. Verify ticket → `EN_COURS`, new `ACTIVE` assignment, handover `COMPLETED`, `acceptedAt` set. Concurrent double-click: one wins, the other gets `400`.

**Maps to**: FR-007, FR-007a, FR-019, FR-019a; SC-004; spec §"Acceptance Scenarios" of US 2 + clarifications Q1, Q3.

### Tests for US2

- [X] T027 [P] [US2] Unit test `HandoverServiceImplAcceptTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplAcceptTest.java`: happy path; cross-zone rejection (`ValidationException` "cross-zone self-accept not allowed"); race condition where `compareAndSetStatus` returns 0 (asserts `ValidationException` "already accepted"); pre-existing `ACTIVE` assignment → reject.
- [ ] T028 [P] [US2] Web slice test `HandoverControllerAcceptTest` in `src/test/java/com/pfe/sageline/controller/HandoverControllerAcceptTest.java`: `@WebMvcTest`, mocked service, assert `@PreAuthorize` rejects `CHEF_SECTEUR` and `ADMIN_IT` (FR-019), accepts `TECH_VAL`.

### Implementation for US2

- [X] T029 [US2] Implement `HandoverServiceImpl.acceptHandover(Long handoverId)` in `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java`:
  1. Load handover (FETCH `validation.productionLine`).
  2. Resolve current user via `SecurityUtils.getCurrentUserId()`; check `user.productionLine == validation.productionLine` else throw `ValidationException` (FR-019a / R3).
  3. Pre-check no other `ACTIVE` assignment exists on the ticket; if found throw `ValidationException` (R1).
  4. Call `handoverRepository.compareAndSetStatus(id, PENDING|ACCEPTED, COMPLETED, currentUser, now())`; if rows-affected == 0 throw `ValidationException("already accepted")`.
  5. Create new `ValidationAssignment(status=ACTIVE)` for the user.
  6. Set ticket → `EN_COURS`.
- [X] T030 [US2] After-commit emission: STOMP `HANDOVER_ACCEPTED` on `/topic/handover.zone.{zoneId}`. No personal notification row needed (the accepter is the actor). Same file as T029.
- [X] T031 [US2] Wire `POST /api/handovers/{handoverId}/accept` in `HandoverController` to call the service. Confirm `@PreAuthorize("hasRole('TECH_VAL')")`. Path: `src/main/java/com/pfe/sageline/controller/HandoverController.java`.
- [ ] T032 [US2] Verify quickstart §5 last step + §6 (race-safety with two concurrent accepts).

**Checkpoint**: A pending handover can be picked up end-to-end; race-safe.

---

## Phase 5: User Story 3 — Voluntary manual handover (Priority: P2)

**Goal**: A `TECH_VAL` who is the active assignee of a ticket can voluntarily initiate a handover with note + summary. Same outcome as US1 but `triggeredBy=MANUAL`.

**Independent Test**: Authenticated as the assignee `TECH_VAL`, `POST /api/handovers/initiate/{validationId}` with body. Verify same post-conditions as US1 with `triggeredBy=MANUAL`.

**Maps to**: FR-002, FR-016; spec US 3.

### Tests for US3

- [ ] T033 [P] [US3] Unit test `HandoverServiceImplInitiateManualTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplInitiateManualTest.java`: happy path; non-assignee `TECH_VAL` rejected; ticket not in `EN_COURS` rejected; pre-existing pending handover rejected (idempotency).
- [ ] T034 [P] [US3] Web slice test `HandoverControllerInitiateTest` for the role gate of `POST /initiate/{id}`.

### Implementation for US3

- [X] T035 [US3] Implement `HandoverServiceImpl.initiateHandover(Long validationId, HandoverInitiateRequest req, TriggerType trigger)` for the manual branch in `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java`:
  1. Load validation; assert `status == EN_COURS` else `ValidationException`.
  2. Resolve current user; if `trigger == MANUAL` assert user IS the active assignee else `ValidationException` (FR-016).
  3. Assert `findActiveByValidation(validationId)` is empty (idempotency, FR-004).
  4. Create `TicketHandover(...)` with caller-supplied note/summary, set ticket and assignment status, persist.
- [X] T036 [US3] After-commit emission: persist personal `Notification` for the outgoing tech, emit STOMP `HANDOVER_TRIGGERED` on personal queue + zone topic with the manual French copy variant.
- [X] T037 [US3] Wire `POST /api/handovers/initiate/{validationId}` in `HandoverController`: in the controller, pick `MANUAL` if caller has `TECH_VAL` and IS active assignee, else `ADMIN_FORCE` if caller has `CHEF_SECTEUR`/`ADMIN_IT`, else `403`. (The `ADMIN_FORCE` branch lights up in US5.)
- [ ] T038 [US3] Verify quickstart §4.

**Checkpoint**: Manual self-initiation works end-to-end.

---

## Phase 6: User Story 4 — Supervisor designates a specific technician (Priority: P2)

**Goal**: A `CHEF_SECTEUR` or `ADMIN_IT` can designate a `TECH_VAL` as the recipient of a `PENDING` handover. The designated tech gets a personal alert and can complete the flow via the existing `/accept` endpoint. List endpoint exposes the queue.

**Independent Test**: Stage a `PENDING` handover; as `CHEF_SECTEUR`, `PATCH /api/handovers/{id}/assign` with `techId`; verify handover `ACCEPTED`, `toTech` populated; designated user receives a `Notification` row. Then `GET /api/handovers/pending` shows the row before assign and the same row marked `ACCEPTED` after.

**Maps to**: FR-014, FR-017; spec US 4.

### Tests for US4

- [ ] T039 [P] [US4] Unit test `HandoverServiceImplAssignTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplAssignTest.java`: happy path; recipient lacking `TECH_VAL` role rejected (edge case); cross-zone designation succeeds (this is the sanctioned escape hatch per FR-019a / `contracts/handover-rest-api.md` §3).
- [ ] T040 [P] [US4] Web slice test for `PATCH /assign` and `GET /pending` role gates.

### Implementation for US4

- [X] T041 [US4] Implement `HandoverServiceImpl.assignHandover(Long handoverId, Long techId)` in `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java`:
  1. Load handover; assert status `PENDING` else `ValidationException`.
  2. Load target user; assert holds `TECH_VAL` role else `ValidationException`.
  3. `compareAndSetStatus(id, PENDING, ACCEPTED, targetUser, null)` — `acceptedAt` stays null until the designated tech actually clicks accept.
- [X] T042 [US4] After-commit emission: persist `Notification` for the designated tech; STOMP `HANDOVER_ASSIGNED` on `/user/{techId}/queue/handover` and on the zone topic.
- [X] T043 [US4] Implement `HandoverServiceImpl.getPendingHandovers()` returning `handoverRepository.findAllPending()` mapped via `HandoverMapper`.
- [X] T044 [US4] Wire `PATCH /api/handovers/{handoverId}/assign` and `GET /api/handovers/pending` in `HandoverController` with the `@PreAuthorize` clauses from `contracts/handover-rest-api.md`.
- [ ] T045 [US4] Verify quickstart §5 (assign + accept).

**Checkpoint**: Supervisor can route stranded tickets explicitly.

---

## Phase 7: User Story 5 — Forced handover by IT administrator (Priority: P3)

**Goal**: An `ADMIN_IT` (or `CHEF_SECTEUR`) can force a handover regardless of the current assignee. Reuses the same initiate endpoint, distinguishes by trigger type.

**Independent Test**: Authenticated as `ADMIN_IT`, `POST /api/handovers/initiate/{id}` against a ticket assigned to someone else. Verify outcome equals US1 with `triggeredBy=ADMIN_FORCE` and the original tech gets a personal alert with the "force" message variant.

**Maps to**: FR-003; spec US 5.

### Tests for US5

- [ ] T046 [P] [US5] Unit test `HandoverServiceImplForceTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplForceTest.java`: happy path under `ADMIN_FORCE` even when caller is not the assignee; non-`EN_COURS` ticket rejected; pre-existing pending rejected.
- [ ] T047 [P] [US5] Web slice asserting `@PreAuthorize` allows the force branch for `ADMIN_IT`/`CHEF_SECTEUR`.

### Implementation for US5

- [X] T048 [US5] Extend `HandoverServiceImpl.initiateHandover` (T035) to handle `trigger == ADMIN_FORCE`: skip the "must be assignee" check, still enforce `EN_COURS` + idempotency, set `triggeredBy=ADMIN_FORCE`. Same file.
- [X] T049 [US5] Update controller dispatch (T037) to choose `ADMIN_FORCE` when caller has `ADMIN_IT` or `CHEF_SECTEUR` AND is not the assignee. Confirm the French "force" copy variant fires in T036.
- [ ] T050 [US5] Verify quickstart §4 with an admin token.

**Checkpoint**: Emergency override path verified.

---

## Phase 8: User Story 6 — Cancellation of a pending handover (Priority: P3)

**Goal**: A supervisor / admin can cancel a non-terminal handover. The ticket returns to `EN_COURS` under the original technician with a reactivated assignment, the queue panel removes the row, and the original tech gets a personal "you're active again" notification.

**Independent Test**: Stage a `PENDING` handover; `PATCH /api/handovers/{id}/cancel` as `CHEF_SECTEUR`; verify ticket `EN_COURS`, original assignment `ACTIVE` again, handover `CANCELLED`.

**Maps to**: FR-008, FR-018; spec US 6.

### Tests for US6

- [ ] T051 [P] [US6] Unit test `HandoverServiceImplCancelTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplCancelTest.java`: happy path from `PENDING` and from `ACCEPTED`; already-terminal status rejected.

### Implementation for US6

- [X] T052 [US6] Implement `HandoverServiceImpl.cancelHandover(Long handoverId)` in `src/main/java/com/pfe/sageline/service/impl/HandoverServiceImpl.java`:
  1. Load handover; assert status in (`PENDING`, `ACCEPTED`).
  2. Set handover → `CANCELLED`.
  3. Find the original technician's `PAUSED` assignment for the validation; flip back to `ACTIVE`.
  4. Set ticket → `EN_COURS`.
- [X] T053 [US6] After-commit emission: persist `Notification` for original tech; STOMP `HANDOVER_CANCELLED` on personal queue + zone topic.
- [X] T054 [US6] Implement `HandoverServiceImpl.autoCancelIfPending(Validation v)` per `research.md` R2 (used by `ValidationService` close / submit-review paths). Reuses the cancel logic but is called inside another transaction. Same file.
- [X] T055 [US6] Wire `PATCH /api/handovers/{handoverId}/cancel` in `HandoverController` with `@PreAuthorize("hasAnyRole('CHEF_SECTEUR','ADMIN_IT')")`.
- [X] T056 [US6] Modify `ValidationService` (existing) close/submit-review methods to call `handoverService.autoCancelIfPending(ticket)` BEFORE changing the ticket's status — implements FR-006a. Path: `src/main/java/com/pfe/sageline/service/impl/ValidationServiceImpl.java` (or wherever the close logic lives).
- [X] T057 [US6] Add a guard in `ValidationService` close/submit-review paths: if ticket is `EN_ATTENTE_HANDOVER` and the caller is NOT the original technician, throw `ValidationException` (FR-006).
- [ ] T058 [US6] Verify quickstart §7 (early closure auto-cancel) and the supervisor cancel path.

**Checkpoint**: Cancellation + auto-cancel-on-closure both work; FR-006a fully wired.

---

## Phase 9: User Story 7 — Handover history & KPIs (Priority: P3)

**Goal**: Ticket detail shows the chronological list of every handover; KPI dashboard exposes count + median/p95 time-to-accept + trigger-type breakdown over a date range, sliceable by zone and technician.

**Independent Test**: With several completed handovers in DB, `GET /api/handovers/validation/{id}` returns chronological list; `GET /api/handovers/kpis?from=...&to=...` returns the populated `HandoverKpiResponse`.

**Maps to**: FR-009, FR-010, FR-021, FR-022; SC-003, SC-006, SC-007; spec US 7.

### Tests for US7

- [ ] T059 [P] [US7] Unit test `HandoverServiceImplHistoryTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplHistoryTest.java`: chronological order; empty list when no handovers.
- [ ] T060 [P] [US7] Unit test `HandoverServiceImplKpiTest` in `src/test/java/com/pfe/sageline/service/HandoverServiceImplKpiTest.java`: counts only non-`CANCELLED` for frequency; `timeToAccept` computed only over `COMPLETED`; median/p95 correctness; zone + technician + trigger-type breakdowns.
- [ ] T061 [P] [US7] Web slice asserting `GET /kpis` is `403` for `TECH_VAL` and OK for `CHEF_SECTEUR`/`EXPERT`/`ADMIN_IT`.

### Implementation for US7

- [X] T062 [US7] Implement `HandoverServiceImpl.getHandoverHistory(Long validationId)` returning `findByValidationOrderByScheduledAtAsc` mapped to `HandoverResponse[]`.
- [X] T063 [US7] Implement `HandoverServiceImpl.getHandoverKpis(LocalDate from, LocalDate to)`:
  1. Convert dates to `LocalDateTime` (start-of-day / end-of-day).
  2. Load `findInRange(from, to)`.
  3. Build counts: total (excluding `CANCELLED`), by `triggerType`, by zone (`validation.productionLine`), by technician (`fromTech`).
  4. Compute `timeToAccept` from `COMPLETED` rows: list of `Duration.between(scheduledAt, acceptedAt).toSeconds()`, sort, pick median + p95.
  5. Return `HandoverKpiResponse`.
- [X] T064 [US7] Wire `GET /api/handovers/validation/{validationId}` and `GET /api/handovers/kpis` in `HandoverController` with the `@PreAuthorize` from `contracts/handover-rest-api.md`.
- [ ] T065 [US7] Optionally expose handover frequency on the existing KPI dashboard endpoint (if `KPIService` aggregates multiple metric families). Path: `src/main/java/com/pfe/sageline/service/KPIService.java`. Skip if the dashboard reads `/api/handovers/kpis` directly.
- [ ] T066 [US7] Verify quickstart §8.

**Checkpoint**: Audit history and KPIs available; SC-003 and SC-007 verifiable from the dashboard.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Cross-story validation and hardening.

- [ ] T067 [P] Add Swagger / `@Operation` annotations to every method in `HandoverController` so endpoints surface in `/swagger-ui.html` with accurate request/response schemas (Constitution §"Technology Stack — API documentation"). Path: `src/main/java/com/pfe/sageline/controller/HandoverController.java`.
- [ ] T068 [P] Review French copy in `HandoverServiceImpl` against `contracts/handover-websocket-events.md` §4. Centralize as `private static final String` constants if duplication appears.
- [ ] T069 Run the full quickstart end-to-end (§§3–8) against a freshly started backend; capture any deviation as a follow-up issue.
- [ ] T070 [P] Confirm `application.properties` schema migration is clean: start the app once with the new entity/enums and verify Hibernate creates the `ticket_handovers` table and the new `handover_note` column on `validation_assignments` without errors. No SQL migration files needed (per `research.md` R8).
- [ ] T071 Run `./mvnw test -DskipTests=false` and ensure the new test classes (T021, T022, T027, T028, T033, T034, T039, T040, T046, T047, T051, T059, T060, T061) all pass.
- [ ] T072 [P] Refresh `CLAUDE.md` at repo root to mention the handover endpoints and the 16:45 cron in the architecture summary (not in the spec-Plan-Backend `CLAUDE.md`, which already points at `plan.md`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Blocks Phase 2 task T025 indirectly (`@EnableScheduling` must exist for the `@Scheduled` job to fire) and the security URL rules.
- **Foundational (Phase 2)**: Blocks ALL user stories. Internally: enums (T003–T006) and entity (T007) must precede repository (T009, T010), mapper (T015), service (T017, T018), controller (T019).
- **User Stories (Phase 3+)**: After Phase 2, US1, US3, US5 are mostly independent of each other. US2 depends on US1 (or any path that produces a `PENDING` handover). US4 depends on US1 (queue produced by initiation paths). US6 depends on US1 and indirectly modifies US3/US5 paths via `autoCancelIfPending`. US7 depends on US1, US2, US6 to have sample data but the code is independent.
- **Polish (Phase 10)**: After all user stories.

### User Story Dependencies

- **US1 (P1)**: Depends only on Phase 2.
- **US2 (P1)**: Depends on Phase 2 + a way to create a `PENDING` handover (US1 or US3). Code-wise: independent.
- **US3 (P2)**: Depends only on Phase 2.
- **US4 (P2)**: Depends on Phase 2 + a `PENDING` handover (US1 or US3). Shares `findAllPending` with the queue panel.
- **US5 (P3)**: Extends US3's `initiateHandover` (T035 → T048). Code-shared file means slight serialization but semantically independent.
- **US6 (P3)**: Depends on Phase 2; modifies `ValidationServiceImpl` (T056) — coordinate with anyone editing validation closure logic.
- **US7 (P3)**: Depends only on Phase 2 in code; needs sample handover data for live testing.

### Within Each User Story

- Tests (T021/T022, T027/T028, …) MUST be written first per Constitution §"Development Workflow" item 5; they should fail before the implementation tasks (T023…) land.
- Service impl before controller wiring before quickstart verification.

### Parallel Opportunities

- **Phase 2** parallel batch: T003, T004, T005, T006 (different files, four enums) can run together. T011, T012, T013, T014 likewise. T016 too. T002 also.
- **Within a user story**, the `[P]` tests can run in parallel with each other but NOT with the implementation that they cover.
- **Across stories**, with multiple developers: Dev A on US1 (T021–T026), Dev B on US3 (T033–T038), Dev C on US7 KPI tests (T060). US2/US4/US6 land after US1/US3 produce sample data.

---

## Parallel Example: Phase 2 Foundational

```bash
# Launch all four enum files together:
Task: "T003 [P] Create HandoverStatus enum in src/main/java/com/pfe/sageline/enums/HandoverStatus.java"
Task: "T004 [P] Create TriggerType enum in src/main/java/com/pfe/sageline/enums/TriggerType.java"
Task: "T005 [P] Add EN_ATTENTE_HANDOVER to TicketStatus enum"
Task: "T006 [P] Add PAUSED to AssignmentStatus enum"

# Then DTOs in parallel:
Task: "T011 [P] HandoverInitiateRequest record"
Task: "T012 [P] HandoverAssignRequest record"
Task: "T013 [P] HandoverResponse record"
Task: "T014 [P] HandoverKpiResponse record"
Task: "T016 [P] HandoverNotificationDto record"
```

---

## Implementation Strategy

### MVP First (US1 + US2 only)

1. Phase 1 + Phase 2.
2. Phase 3 (US1 — auto sweep) → demo idempotent 16:45 trigger.
3. Phase 4 (US2 — self-accept) → end-to-end pickup demo with race-safety test.
4. **STOP and VALIDATE** against quickstart §§3, 5 last step, 6.

This MVP delivers the headline value of the feature: tickets are no longer abandoned at shift end, and they get picked up.

### Incremental Delivery

5. Add Phase 5 (US3 — manual initiate) → richer demo flow.
6. Add Phase 6 (US4 — supervisor assign) → live queue panel useful for the supervisor.
7. Add Phase 7 (US5 — admin force) → operational override.
8. Add Phase 8 (US6 — cancel + auto-cancel-on-closure) → completes FR-006a.
9. Add Phase 9 (US7 — history & KPIs) → PFE-defence dashboard.
10. Phase 10 (polish) → swagger, copy review, final regression.

### Parallel Team Strategy

- Two developers: Dev A drives the backbone (US1 → US2 → US6), Dev B drives the side paths (US3 → US4 → US5) then KPIs (US7). Phase 10 split by `[P]` markers.

---

## Notes

- `[P]` = different files, no dependency on incomplete tasks.
- `[Story]` label aligns each task to its `spec.md` user story.
- Constitution Principle II requires `@PreAuthorize` on every mutating endpoint — verified in T031, T037, T044, T049, T055, T064 and reinforced by web-slice tests T028/T034/T040/T047/T061.
- Constitution Principle V (idempotency) is structurally enforced by T010's `NOT EXISTS` query, T009's `compareAndSetStatus`, and T021's idempotency assertion.
- Tests target unit + slice level (per `research.md` R9). Integration tests with a real Postgres are deferred.
- The frontend tasks (`Plan.md` §Phase 4) are out of scope here and live in their own follow-up tasks file.
