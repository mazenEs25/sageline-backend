# Implementation Plan: Shift-End Ticket Handover

**Branch**: `001-ticket-handover` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-ticket-handover/spec.md`
**Source plan**: `Plan.md` at repo root (Phases 2–5 — data model, backend, frontend, tasks)
**Constitution**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) v1.0.0

## Summary

Add a structured shift-end handover protocol to the existing SageLine
ticket validation system. A new `TicketHandover` entity tracks every
transfer (automatic / manual / forced), the `TicketStatus` and
`AssignmentStatus` enums gain `EN_ATTENTE_HANDOVER` and `PAUSED`
respectively, a Spring `@Scheduled` job (`0 45 16 * * MON-FRI`)
sweeps in-progress tickets at 16:45, and supervisors get a real-time
queue panel powered by STOMP over the existing `/ws` endpoint.
The five `/speckit-clarify` answers tighten the design: race-safe
acceptance via DB constraint, atomic auto-cancel on early closure,
zone-locality for self-accept, persisted personal notifications, and
KPIs covering frequency + median/p95 time-to-accept + trigger-type
breakdown.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 4.0.2, Spring Data JPA,
Spring Security OAuth2 Resource Server (Keycloak JWT), Spring
WebSocket / STOMP, Spring Scheduling, Lombok, Maven (`./mvnw`)
**Storage**: PostgreSQL `sageLine_db` on `localhost:5432` (user
`postgres`, ddl-auto `update`)
**Testing**: JUnit 5 + Spring Boot Test (existing project default —
tests skipped by pom config; explicit run via `./mvnw test
-DskipTests=false`)
**Target Platform**: Linux/Windows server JVM, app on port `8089`
**Project Type**: Web service (Spring Boot backend) consumed by an
Angular 18 + PrimeNG frontend on `http://localhost:4200`
**Performance Goals**: shift-end sweep completes in <5 s for up to
500 in-progress tickets; KPI endpoint <3 s for a 30-day window
(SC-007); WebSocket broadcast latency <1 s on local network
**Constraints**: enum changes only (no destructive schema
migration); idempotent scheduler; transactional acceptance with DB
guard preventing two `ACTIVE` assignments on the same validation;
zone-locality enforced at service layer; personal `Notification`
rows persisted, zone-level alerts live-only
**Scale/Scope**: ~5 production lines, ~50 active `TECH_VAL` users,
~200 in-progress tickets at peak; ≤10 handovers/day expected during
normal operations, with 16:45 spike

No `NEEDS CLARIFICATION` items remain — all five Phase-0 questions
were resolved by `/speckit-clarify` (see `spec.md` §Clarifications).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Reviewed against constitution v1.0.0 — all five principles.

| # | Principle | Status | Evidence in this plan |
|---|---|---|---|
| I | Layered Architecture Discipline | ✅ PASS | New files split across `entity/`, `enums/`, `repository/`, `service/` + `service/impl/`, `controller/`, `dtos/request/`, `dtos/response/`, `mappers/`, `scheduler/`. Controllers return DTOs only; mappers do entity↔DTO conversion. |
| II | Role-Gated Security (Keycloak JWT) | ✅ PASS | Every mutating endpoint annotated with `@PreAuthorize` per the role map below; current user resolved via `SecurityUtils.getCurrentUserId()`; never trust client-supplied user IDs. |
| III | State Machine Integrity | ✅ PASS | `EN_ATTENTE_HANDOVER` added to `TicketStatus`, `PAUSED` added to `AssignmentStatus`, `HandoverStatus` and `TriggerType` are closed enums. All transitions go through `HandoverService` methods — no ad-hoc status mutations. Frozen-state rule encoded in service guards. |
| IV | Real-Time Observability & KPI Feedback | ✅ PASS | Every state change emits a typed `HandoverNotificationDto` over `/user/{userId}/queue/handover` (personal) or `/topic/handover.zone.{zoneId}` (zone). KPI metrics extend `KPIService`; `/api/handovers/kpis` exposed. |
| V | Transactional & Idempotent Operations | ✅ PASS | All `HandoverService` mutating methods `@Transactional`; `ShiftEndHandoverJob` skips tickets that already have a non-terminal `TicketHandover`; acceptance race resolved by DB-level partial unique index on `(validation_id) WHERE assignment_status = 'ACTIVE'` enforced via repository pre-check + JPA optimistic lock on the handover row. |

**Constitution gate result: PASS** — no Complexity Tracking entries needed.

### Post-design re-check (after Phase 1 artifacts produced)

Re-evaluated against `data-model.md` and the two contract files:

- **I — Layered Architecture**: confirmed by the file tree above and
  by the DTO records in `contracts/handover-rest-api.md`. ✅
- **II — Role-Gated Security**: every endpoint in
  `contracts/handover-rest-api.md` carries an explicit
  `@PreAuthorize` clause. ✅
- **III — State Machine Integrity**: `data-model.md` §1 documents the
  closed `HandoverStatus` lifecycle and the `TicketStatus` insertion
  point. All transitions go through `HandoverServiceImpl` per
  `research.md` R1/R2/R3. ✅
- **IV — Real-Time Observability & KPI Feedback**: every state change
  has a typed event in `contracts/handover-websocket-events.md` §3;
  KPI contract in `handover-rest-api.md` §7 covers SC-003 + SC-007.
  ✅
- **V — Transactional & Idempotent Operations**: race-safety encoded
  in `compareAndSetStatus` (data-model §7), scheduler idempotency
  encoded in `findEligibleForShiftEndHandover` (data-model §7), all
  service methods `@Transactional` per `research.md` R6. ✅

No regressions; gate still PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-ticket-handover/
├── plan.md              # This file (/speckit-plan output)
├── spec.md              # /speckit-specify + /speckit-clarify output
├── research.md          # Phase 0 output (this command)
├── data-model.md        # Phase 1 output (this command)
├── quickstart.md        # Phase 1 output (this command)
├── contracts/           # Phase 1 output (this command)
│   ├── handover-rest-api.md
│   └── handover-websocket-events.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (repository root)

This is a web-service project. Backend lives at the SageLine repo
root; frontend lives in the sibling Angular project. The handover
feature only **adds** files (and modifies a small set of existing
ones — listed below) without changing the layout.

```text
sageLine-backend/                                  # Spring Boot service (port 8089)
└── src/main/java/com/pfe/sageline/
    ├── entity/
    │   ├── TicketHandover.java                    # NEW
    │   └── ValidationAssignment.java              # MODIFY: add handoverNote
    ├── enums/
    │   ├── HandoverStatus.java                    # NEW
    │   ├── TriggerType.java                       # NEW
    │   ├── TicketStatus.java                      # MODIFY: add EN_ATTENTE_HANDOVER
    │   └── AssignmentStatus.java                  # MODIFY: add PAUSED
    ├── repository/
    │   ├── HandoverRepository.java                # NEW
    │   └── ValidationRepository.java              # MODIFY: add findByStatusWithActiveAssignment
    ├── service/
    │   ├── HandoverService.java                   # NEW (interface)
    │   └── impl/HandoverServiceImpl.java          # NEW
    ├── controller/
    │   └── HandoverController.java                # NEW
    ├── dtos/
    │   ├── request/HandoverInitiateRequest.java   # NEW
    │   ├── request/HandoverAssignRequest.java     # NEW
    │   └── response/HandoverResponse.java         # NEW
    ├── scheduler/
    │   └── ShiftEndHandoverJob.java               # NEW
    ├── mappers/
    │   └── HandoverMapper.java                    # NEW
    ├── Config/
    │   └── SecurityConfig.java                    # MODIFY: handover URL rules
    └── SagelineApplication.java                   # MODIFY: @EnableScheduling

sageLine-frontend/ (sibling Angular project)      # Out of scope for backend plan;
                                                  # see Plan.md §Phase 4 for components.
```

**Structure Decision**: web-service (Spring Boot) layered project.
Backend additions follow the existing package conventions exactly
(`com.pfe.sageline.<layer>`) — see Constitution Principle I.
Frontend changes are governed by `Plan.md` §Phase 4 and are out of
scope for this backend-focused plan.

## Role Map (Authorization Matrix)

Used for all `@PreAuthorize` annotations on `HandoverController` and
the corresponding `SecurityConfig` URL rules.

| Operation | Endpoint | Roles |
|---|---|---|
| Initiate handover (manual) | `POST /api/handovers/initiate/{validationId}` | `TECH_VAL`, `ADMIN_IT` |
| Force handover | (same endpoint, body distinguishes trigger) | `CHEF_SECTEUR`, `ADMIN_IT` |
| Self-accept | `POST /api/handovers/{handoverId}/accept` | `TECH_VAL` (same-zone only — service guard) |
| Designate recipient | `PATCH /api/handovers/{handoverId}/assign` | `CHEF_SECTEUR`, `ADMIN_IT` |
| Cancel | `PATCH /api/handovers/{handoverId}/cancel` | `CHEF_SECTEUR`, `ADMIN_IT` |
| List pending | `GET /api/handovers/pending` | `CHEF_SECTEUR`, `ADMIN_IT` |
| Ticket history | `GET /api/handovers/validation/{validationId}` | All authenticated roles |
| KPIs | `GET /api/handovers/kpis` | `CHEF_SECTEUR`, `EXPERT`, `ADMIN_IT` |

## Complexity Tracking

> **Empty** — Constitution Check passed without violations. No
> complexity entries to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none)    | —          | —                                   |
