<!--
SYNC IMPACT REPORT
==================
Version change: (uninitialized template) → 1.0.0
Bump rationale: MAJOR — initial ratification of the SageLine constitution.
                All placeholder tokens replaced with concrete content scoped
                to the Handover System feature and the existing SageLine
                backend/frontend architecture.

Modified principles:
  - [PRINCIPLE_1_NAME]  → I. Layered Architecture Discipline
  - [PRINCIPLE_2_NAME]  → II. Role-Gated Security (Keycloak JWT)
  - [PRINCIPLE_3_NAME]  → III. State Machine Integrity
  - [PRINCIPLE_4_NAME]  → IV. Real-Time Observability & KPI Feedback
  - [PRINCIPLE_5_NAME]  → V. Transactional & Idempotent Operations

Added sections:
  - Technology Stack & Architectural Constraints (replaces SECTION_2)
  - Development Workflow & Quality Gates           (replaces SECTION_3)

Removed sections: none

Templates requiring updates:
  - .specify/templates/plan-template.md     ⚠ pending — Constitution Check
                                            section should reference the five
                                            principles below when filled in by
                                            /speckit-plan.
  - .specify/templates/spec-template.md     ✅ no change required (scope agnostic).
  - .specify/templates/tasks-template.md    ✅ no change required (categories
                                            already cover entity/service/
                                            controller/scheduler/frontend).
  - .specify/templates/checklist-template.md ✅ no change required.
  - Plan.md (feature plan)                  ✅ aligned — task order and file
                                            layout already conform.

Follow-up TODOs: none. RATIFICATION_DATE set to 2026-05-05 (today).
-->

# SageLine Constitution

> Scope: this constitution governs the **SageLine Handover System** feature
> (Spec Kit driven) and any subsequent feature delivered into the SageLine
> Spring Boot backend and Angular frontend. It encodes the non-negotiable
> rules already implicit in the existing codebase so future work cannot
> drift from the established architecture.

## Core Principles

### I. Layered Architecture Discipline

Every feature MUST respect the established layering: **Controller → Service →
Repository → Entity**. Controllers expose REST under `/api/`, contain no
business logic, and accept/return DTOs only — never JPA entities. Services
hold the business logic, are annotated `@Transactional` on mutating methods,
and orchestrate repositories. Repositories extend Spring Data JPA and use
JPQL with `LEFT JOIN FETCH` to avoid N+1 issues. Entities live in `entity/`
with Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`,
`@Builder`) and never carry presentation logic.

DTOs MUST be split into `dtos/request/` and `dtos/response/`. Conversion
between entity and DTO MUST go through a dedicated class in `mappers/` —
never inline inside controllers or services. The Handover feature, like any
other, MUST add files in the same package layout (`entity/`,
`repository/`, `service/` + `service/impl/`, `controller/`, `dtos/...`,
`mappers/`, `scheduler/`, `enums/`).

**Rationale:** Layering is the contract that lets six entities (Validation,
Assignment, Handover, …) evolve independently without bleeding concerns.
Bypassing it (e.g. exposing entities from controllers) instantly breaks
Keycloak role gating, JSON serialization stability, and audit traceability.

### II. Role-Gated Security (Keycloak JWT)

Authentication is delegated to Keycloak (`realm: sageline`, OAuth2 JWT).
The `KeycloakJwtConverter` maps `realm_access.roles` to `ROLE_<name>`
authorities. Every state-changing controller endpoint MUST be guarded with
`@PreAuthorize` naming the explicit roles permitted — `SecurityConfig` URL
rules are a defence-in-depth layer, not the primary gate.

The role hierarchy is fixed: `ADMIN_IT`, `EXPERT`, `CHEF_SECTEUR`,
`TECH_PREP`, `TECH_VAL`, `RESPONSABLE`. Handover endpoints MUST follow
the role mapping declared in `Plan.md` §"REST Endpoints — HandoverController"
(e.g. `/initiate` → `TECH_VAL, ADMIN_IT`; `/assign` → `CHEF_SECTEUR,
ADMIN_IT`). The current user MUST be resolved via `SecurityUtils.getCurrentUserId()`
(which looks up the DB user by `keycloakId`) — never via raw JWT parsing
inside services.

**Rationale:** Validation tickets carry production-line accountability;
permitting a `TECH_VAL` to force-cancel another tech's handover, or letting
a service trust a client-supplied `userId`, would silently corrupt the
audit chain that the entire KPI dashboard depends on.

### III. State Machine Integrity

The ticket lifecycle is a finite state machine encoded in the
`TicketStatus` enum. Transitions MUST happen only through dedicated
service methods that emit a single, named state change. Ad-hoc
mutation of `validation.status` from controllers, schedulers, or
mappers is forbidden.

The Handover feature extends — does not bypass — this machine: it adds
`EN_ATTENTE_HANDOVER` to `TicketStatus` and `PAUSED` to `AssignmentStatus`,
and the lifecycle becomes:

```
PLANIFIE → EN_PREP → PRET → EN_COURS → EN_ATTENTE_HANDOVER → EN_COURS (new tech)
                                                            ↘ EN_REVUE (skipped)
                          → EN_REVUE → CONFORME | NON_CONFORME | ANNULE
```

While a ticket is in `EN_ATTENTE_HANDOVER` it is **frozen**: no
`ValidationResult` may be persisted, no review submitted, no closure
performed. Likewise `HandoverStatus` (`PENDING → ACCEPTED → COMPLETED |
CANCELLED`) and `TriggerType` (`MANUAL | SHIFT_END_AUTO | ADMIN_FORCE`)
are closed sets — new values require an amendment of this constitution
or an explicit, documented exception in the feature plan.

**Rationale:** The state machine is what makes audit history meaningful.
A ticket that skips `EN_ATTENTE_HANDOVER` and transitions directly between
techs is indistinguishable in storage from a fraudulent reassignment.

### IV. Real-Time Observability & KPI Feedback

Every state-changing service operation that affects a ticket, an
assignment, a handover, or a notification MUST emit a STOMP message via
`SimpMessagingTemplate` on the canonical topic prefix:

- Personal alerts → `/user/{userId}/queue/...`
- Zone / supervisor alerts → `/topic/<domain>.zone.{zoneId}`
- Broadcast events → `/topic/<domain>.<event>`

For Handover specifically, the contract is fixed by `Plan.md` §"WebSocket
Events": `/user/{userId}/queue/handover` for personal alerts and
`/topic/handover.zone.{zoneId}` for supervisors. Payloads MUST be a typed
DTO (e.g. `HandoverNotificationDto`), never a raw map.

KPIs MUST recalculate automatically on terminal lifecycle events
(`KPIService` already does this on validation closure). Adding the
Handover feature MUST extend `KPIService` with handover-frequency metrics
per zone, technician, and time period, and expose them at
`GET /api/handovers/kpis`.

**Rationale:** The supervisor dashboard and the PFE defence depend on
the live queue panel and KPI charts. Silent state changes — even
correct ones — break the demo and erode operator trust.

### V. Transactional & Idempotent Operations

All mutating service methods MUST be `@Transactional`; read-only
queries SHOULD be `@Transactional(readOnly = true)`. A single business
operation (e.g. "trigger auto-handover for ticket X") MUST commit
atomically — partial state (handover row created but ticket status
unchanged) is forbidden.

Scheduled jobs MUST be idempotent. The `ShiftEndHandoverJob`
(`@Scheduled(cron = "0 45 16 * * MON-FRI")`) MUST skip any
`Validation` that already has a non-terminal `TicketHandover`, and
MUST NOT emit duplicate WebSocket notifications on re-runs. Manual
and forced handovers MUST share the same idempotency guard so a
`TECH_VAL` clicking twice cannot create two `PENDING` rows.

`@EnableScheduling` MUST remain on `SagelineApplication`. New cron
jobs MUST live under `scheduler/` and be unit-testable in isolation
(no static state, dependencies injected via constructor / Lombok
`@RequiredArgsConstructor`).

**Rationale:** A scheduler that fires at 16:45 across a restart, a
clock skew, or a manual replay is the single highest-risk surface in
the feature. Non-idempotent behaviour here would page every
supervisor twice and pollute KPI counts permanently.

## Technology Stack & Architectural Constraints

The following stack is fixed for the duration of this constitution.
Any deviation requires a MAJOR amendment.

- **Backend:** Java 17, Spring Boot 4.0.2, Spring Data JPA, Spring
  Security OAuth2 Resource Server, Spring WebSocket (STOMP), Lombok,
  Maven (`./mvnw`).
- **Database:** PostgreSQL `sageLine_db` on `localhost:5432`. JPA
  uses `spring.jpa.hibernate.ddl-auto=update`; schema migrations are
  driven by entity changes plus, when needed, a manual SQL
  follow-up. Adding `EN_ATTENTE_HANDOVER` and `PAUSED` enum values
  is a non-breaking column-content change.
- **Auth:** Keycloak on `http://localhost:8180`, realm `sageline`,
  client `admin-cli`. The `KeycloakAdminConfig` admin client is the
  only path for server-side user management.
- **External AI service:** Python ML at `http://localhost:5000`,
  consumed by `AIPredictionService` with graceful fallback. Handover
  MUST NOT introduce a hard dependency on this service.
- **Frontend:** Angular at `http://localhost:4200` (only origin
  allowed by CORS), PrimeNG components. Handover screens MUST live
  under `src/app/pages/Handover/` and use the shared `HandoverService`,
  models, and enums per `Plan.md` §"Files to create (frontend)".
- **API documentation:** Swagger UI at
  `http://localhost:8089/swagger-ui.html` — every new controller
  endpoint MUST appear there with accurate request/response schemas.
- **Logging:** SQL logging stays on (`spring.jpa.show-sql=true`,
  `hibernate.format_sql=true`) in development; production
  toggling is out of scope for this feature.

Error contract: domain failures MUST be raised as
`ResourceNotFoundException` (404) or `ValidationException` (400)
and translated by `GlobalExceptionHandler`. Handover endpoints
MUST NOT introduce new top-level exception types without amending
the global handler.

## Development Workflow & Quality Gates

1. **Spec-Driven flow.** Every feature follows the Spec Kit chain:
   `/speckit-constitution` → `/speckit-specify` →
   (`/speckit-clarify`) → `/speckit-plan` → `/speckit-tasks` →
   `/speckit-implement`. The Handover feature uses `Plan.md` as the
   authoritative source for entities, endpoints, scheduler, and
   acceptance criteria; downstream artifacts MUST trace back to it.

2. **Branching.** Each feature lives on its own branch named per
   `speckit-git-feature` conventions. Direct commits to `master` are
   restricted to constitution updates and release tags.

3. **Constitution Check gate.** Every `plan.md` produced by
   `/speckit-plan` MUST include a Constitution Check section that
   evaluates the feature against Principles I–V. A violation MUST
   either be removed or justified in the plan's Complexity Tracking
   table — unjustified violations block `/speckit-tasks`.

4. **Acceptance criteria.** Features MUST ship BDD-style scenarios
   (Given/When/Then) covering happy path, role gating, idempotency,
   and the WebSocket contract. The five scenarios in `Plan.md` §
   "Phase 5" are the reference shape.

5. **Build & test commands** (Windows / Bash):

   ```bash
   ./mvnw spring-boot:run         # run on :8089
   ./mvnw clean package           # build (tests skipped by pom config)
   ./mvnw test -DskipTests=false  # explicit test run
   ./mvnw compile                 # compile only
   ```

   Implementation work MUST at minimum compile cleanly and start the
   app locally before being marked complete. Adding tests is
   encouraged; removing or disabling existing ones requires a written
   justification in the PR description.

6. **Code review.** PRs MUST verify:
   - Layering rules (Principle I).
   - `@PreAuthorize` present on every new mutating endpoint
     (Principle II).
   - State transitions go through service methods only
     (Principle III).
   - WebSocket emission and KPI hooks present where required
     (Principle IV).
   - `@Transactional` and idempotency for schedulers
     (Principle V).

7. **No backwards-compatibility shims** beyond what is strictly
   needed: enum additions, new columns with sensible defaults, and
   new endpoints are preferred over renaming or breaking existing
   surfaces during this feature.

## Governance

This constitution supersedes ad-hoc conventions, individual
preferences, and undocumented tribal knowledge. When a code review,
plan, or implementation conflicts with both this document and an
older comment / commit message, this document wins.

**Amendment procedure.** Amendments are proposed by editing
`.specify/memory/constitution.md` via `/speckit-constitution`. Each
amendment MUST:

1. Update the version per the policy below.
2. Update `LAST_AMENDED_DATE`.
3. Prepend a Sync Impact Report (HTML comment) describing changes
   and downstream artifacts touched.
4. Propagate consequences into `.specify/templates/*` and any
   guidance docs (`CLAUDE.md`, `README.md`, `Plan.md`) in the same
   change set.

**Versioning policy** (semantic):

- **MAJOR**: removing a principle, redefining its meaning, or
  changing the fixed technology stack.
- **MINOR**: adding a new principle or materially expanding an
  existing one (e.g. new mandatory section).
- **PATCH**: clarifications, wording, typo fixes, non-semantic
  refinements.

**Compliance review.** During `/speckit-plan`, `/speckit-analyze`,
and code review, reviewers MUST cite specific principle numbers
when raising objections, and MUST treat unjustified Constitution
Check failures as blocking. Complexity that violates a principle
must be entered in the plan's Complexity Tracking table with a
concrete reason and a rejected simpler alternative.

For day-to-day runtime guidance (build commands, package layout,
auth bootstrap), see `CLAUDE.md` at the repository root and
`spec-Plan-Backend/Plan.md` for the active feature plan.

**Version**: 1.0.0 | **Ratified**: 2026-05-05 | **Last Amended**: 2026-05-05
