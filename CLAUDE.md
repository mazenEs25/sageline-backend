# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SageLine is a Spring Boot 4.0.2 application for production line quality validation with AI-powered non-conformity prediction and KPI tracking. It uses PostgreSQL, Keycloak for authentication, and integrates with an external Python ML service.

## Build & Run Commands

```bash
# Run the application (port 8089)
./mvnw spring-boot:run

# Build JAR (tests are skipped via pom.xml config)
./mvnw clean package

# Run tests explicitly
./mvnw test -DskipTests=false

# Compile only
./mvnw compile
```

## Prerequisites

- Java 17
- PostgreSQL database `sageLine_db` on localhost:5432 (user: `postgres`, password: `123456`)
- Keycloak on `http://localhost:8180`, realm `sageline`, client `admin-cli` (admin/admin)
- External Python ML service on `http://localhost:5000` (optional — `AIPredictionService` falls back gracefully)
- Frontend Angular app expected on `http://localhost:4200` (CORS allowed)

## Architecture

**Layered architecture:** Controller → Service → Repository → Entity

- **Package root:** `com.pfe.sageline`
- **Controllers** (`controller/`): REST endpoints under `/api/`
- **Services** (`service/`): Business logic with `@Transactional`
- **Repositories** (`repository/`): Spring Data JPA with custom `@Query` methods (JPQL with `LEFT JOIN FETCH` for eager loading)
- **Entities** (`entity/`): JPA entities with Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`)
- **DTOs** (`dtos/request/` and `dtos/response/`): Separated request and response DTOs
- **Mappers** (`mappers/`): Manual entity ↔ DTO conversion
- **Exception** (`exception/`): `GlobalExceptionHandler` (`@RestControllerAdvice`) — throws `ResourceNotFoundException` (404), `ValidationException` (400), `TransitionBlockedException` (422), `BatchMeasureValidationException` (multi-row batch failures), `MeasureNotEditableException` (ticket not in EN_COURS)
- **Config** (`Config/`): Security, Keycloak, WebSocket

## Security & Authentication

Authentication is fully wired via Keycloak OAuth2 JWT:

- `KeycloakJwtConverter` — extracts `realm_access.roles` from JWT and maps them to `ROLE_<name>` Spring authorities
- `SecurityUtils` — component for getting current user from JWT (`getCurrentUserId()`, `getCurrentUsername()`, `getCurrentRoles()`). `getCurrentUserId()` looks up the DB user by `keycloakId` (Keycloak UUID sub claim). Call `GET /api/users/me` first to sync a new Keycloak user into the DB.
- `KeycloakAdminConfig` — provides a `Keycloak` admin client bean for user management via Keycloak Admin REST API
- CORS is configured for `http://localhost:4200` only

**Role hierarchy** (from `Role` enum): `ADMIN_IT`, `EXPERT`, `CHEF_SECTEUR`, `TECH_PREP`, `TECH_VAL`, `RESPONSABLE`

## Data Model

```
ProductionLine → Secteur → Phase
ProductionLine → ValidationZone → Validation (ticket)
Validation → ValidationResult, ValidationAssignment, NonConformityPrediction
User → ProductionLine (belongs to)
Conversation → Message
```

- **Validation** is the core ticket entity. Uses `TicketStatus` (not the old `ValidationStatus`). Auto-generates a `ticketCode` (e.g. `VAL-2026-0001`) via `TicketCodeGenerator`.
- **ValidationAssignment** links users to a validation ticket with an `AssignmentStatus`.
- **AnomalyDetection** and **ToolRecommendation** are standalone AI-output entities.
- **Notification** and **Conversation/Message** support the real-time messaging feature.

### Ticket Workflow (`TicketStatus` enum)

```
PLANIFIE → EN_ATTENTE_PREP → PREP_VALIDEE → EN_COURS → EN_ATTENTE_HANDOVER ↔ EN_COURS
                                                      ↓
                                                   EN_REVUE → CONFORME | NON_CONFORME | ANNULE
```

Workflow transitions (controller endpoints, role-gated):
- `PATCH /api/validations/{id}/start-prep` — TECH_PREP or ADMIN_IT
- `PATCH /api/validations/{id}/validate-prep` — TECH_PREP or ADMIN_IT
- `PATCH /api/validations/{id}/start` — TECH_VAL or ADMIN_IT
- `PATCH /api/validations/{id}/submit-review` — TECH_VAL or ADMIN_IT (FR-006a: original tech can submit even from EN_ATTENTE_HANDOVER, which auto-cancels the handover)
- `PATCH /api/validations/{id}/close` — CHEF_SECTEUR, EXPERT, or ADMIN_IT
- `PATCH /api/validations/{id}/cancel` — CHEF_SECTEUR or ADMIN_IT

### Handover Workflow (`TicketHandover` + `HandoverStatus`)

`EN_COURS → EN_ATTENTE_HANDOVER` when a handover is initiated (manual, auto, force). `EN_ATTENTE_HANDOVER → EN_COURS` when accepted or cancelled.

Handover endpoints (`/api/handovers`):
- `POST /api/handovers/initiate/{validationId}` — TECH_VAL (manual) or CHEF_SECTEUR/ADMIN_IT (force)
- `POST /api/handovers/{handoverId}/accept` — TECH_VAL (same production-line only — service guard)
- `PATCH /api/handovers/{handoverId}/assign` — CHEF_SECTEUR, ADMIN_IT
- `PATCH /api/handovers/{handoverId}/cancel` — CHEF_SECTEUR, ADMIN_IT
- `GET /api/handovers/pending` — CHEF_SECTEUR, ADMIN_IT
- `GET /api/handovers/validation/{validationId}` — any authenticated
- `GET /api/handovers/kpis?from=&to=` — CHEF_SECTEUR, EXPERT, ADMIN_IT

**Cron job**: `ShiftEndHandoverJob` fires every weekday at 16:45 (`0 45 16 * * MON-FRI`), sweeps all `EN_COURS` tickets, and initiates `SHIFT_END_AUTO` handovers idempotently. Requires `@EnableScheduling` (on `SagelineApplication`).

**STOMP events**: `HANDOVER_TRIGGERED`, `HANDOVER_ASSIGNED`, `HANDOVER_ACCEPTED`, `HANDOVER_CANCELLED` — personal queue `/user/{userId}/queue/handover` + zone broadcast `/topic/handover.zone.{lineId}`.

## Key Services

- `PosteCatalogService` — manages the reference catalog of measure templates per `PosteType`; read-only by any authenticated role; create/update/delete restricted to `ADMIN_IT`/`CHEF_SECTEUR`; supports batch atomic operations (`/api/poste-catalog/**` endpoints)
- `AIPredictionService` — calls Python ML service at `/predict`, calculates deviations, falls back to defaults if unavailable
- `KPIService` — conformity rate calculations, dashboard generation, auto-recalculates on validation closure
- `ValidationService` — orchestrates the full ticket lifecycle, triggers AI predictions and KPI updates
- `HandoverService` / `HandoverServiceImpl` — full shift-end handover protocol (US1–US7)
- `AnomalyDetectionService` / `ToolRecommendationService` — AI-output management
- `NotificationService` — creates and pushes notifications over WebSocket
- `ValidationMeasureService` — manages bounded-tolerance industrial measures per ticket; auto-classifies via `MeasureDeviationCalculator`; gated to EN_COURS by `MeasureEditabilityGuard` (Phase 002). After every mutation, publishes a coalesced `WorkflowReadinessDTO` snapshot to `/topic/validation.{id}.readiness` via STOMP (after commit) so the frontend readiness bar stays live without polling.
- `WorkflowReadinessService` / `TicketTransitionGuard` (Phase 003) — rule-based pre-check before status transitions; aggregates `TransitionRule` implementations (`MandatoryMeasureCoverageRule`, `SourceStatusRule`) into a `WorkflowReadinessDTO`. `ValidationService` calls `transitionGuard.check(id, EN_REVUE)` before manual `submit-review` AND before auto-advance when all results are done — a blocked auto-advance is swallowed (logged + ticket stays in `EN_COURS`), a blocked manual call throws `TransitionBlockedException` (HTTP 422). Probe readiness via `GET /api/validations/{id}/readiness?targetStatus=EN_REVUE`. Also exposes `computePosteReadiness(validationId, zoneId)` used by `PosteValidationController`.
- `LogImportService` / `LogImportPipeline` (Phase 004) — Sagemcom log ingestion for BNFT/BWC/BTF formats. `HeaderSniffer` picks a `LogFormatStrategy`; `BlockFormatParser` extracts measures; `MeasureMatcher` resolves codes via `PosteMeasureCatalog` + `MeasureCodeAlias`; `SourceStatusReconciler` updates each `ValidationMeasure.sourceDeclaredStatus`. Two-step UX: `POST /preview-log` (dry run, returns `LogImportReportDTO` with matched/unmatched/out-of-range/would-overwrite) then `POST /import-log` with `LogImportOptionsDTO(overwriteExisting)`. `ImportLockService` serializes concurrent imports per validation. Storage root, max size, snippet line count under `sageline.import.*` (see `LogImportProperties`). `MeasureSourceController` exposes `GET /api/validations/{validationId}/measures/{measureId}/source-snippet`.

### Per-Poste Measure Scoping (Phase 005 — V5.0 migration)

`ValidationMeasure` now carries a `poste_status_id` FK to `ValidationPosteStatus` (added in `V5.0__measure_poste_status_link.sql`). This scopes each measure explicitly to one poste of the line ticket.

**New controller:** `PosteValidationController` at `/api/validations/{validationId}/postes/{zoneId}`:
- `GET /measures` — measures for a specific poste (empty list, not 404, when none exist)
- `GET /readiness` — per-poste `WorkflowReadinessDTO`; `canTransition = true` when all mandatory measures for that poste are not `NOT_EXECUTED` (OUT_OF_RANGE measures are reported but do not block closure)

**Ticket-level mutation endpoints deprecated** (`ValidationMeasureController`): `POST /measures`, `POST /measures/batch`, `POST /measures/from-template`, `POST /measures/from-template/{templateId}` are deprecated in favour of the per-poste paths. They still work but respond with `Deprecation: true` and `Sunset` headers (RFC 8594 draft) and log a `WARN` with the caller's `User-Agent`/`X-Requested-By` for migration tracking. The read endpoint (`GET /measures`) is not deprecated — it stays as the ticket-wide aggregate view.

**New endpoint:** `PUT /api/validations/{validationId}/measures/batch` (`BatchUpdateMeasureRequest`) — bulk-update `measuredValue` on existing rows. Returns `BatchValidationMeasureResponse` with per-row `status: "ok"|"error"` so the UI can highlight failing rows without rejecting the whole batch (frontend "Édition groupée → Enregistrer tout" flow).

**Measure-to-poste resolution strategy** in `ValidationMeasureServiceImpl`:
- Mode A: explicit `templateId` → match poste whose `zone.posteType` = `template.posteType`
- Mode B: `measureCode` → walk every poste of the line, use first catalog hit
- Legacy fallback: for pre-migration tickets with no `posteStatuses`, falls back to `validationZone.posteType`

**Idempotency**: `instantiateFromCatalog` is now line-wide — seeds every poste, not just the first. Per-poste deduplication uses `findCatalogTemplateIdsPresentOnPoste`. The uniqueness index `uq_vm_natural_key` was recreated to include `COALESCE(poste_status_id, -1)` so two postes can each have a measure with the same code (e.g. `TEMPS_TEST`).

## WebSocket

- Config in `WebSocketConfig`: STOMP broker at `/ws`, app prefix `/app`, topic prefix `/topic`
- `WebSocketEventListener` handles connect/disconnect
- `WebSocketController` handles messaging endpoints under `/app/`
- Notifications pushed server-side via `SimpMessagingTemplate`

## Key Patterns

- All entities use Lombok — no manual getters/setters
- DTOs split into `dtos/request/` and `dtos/response/` packages
- **Schema management**: Flyway owns all database migrations via `src/main/resources/db/migration/V*.sql` files; `spring.jpa.hibernate.ddl-auto=validate` (strict — never `update` or `create`); Flyway auto-creates baseline on first run
- SQL logging is enabled (`spring.jpa.show-sql=true`, `hibernate.format_sql=true`)
- Repository queries use JPQL with `LEFT JOIN FETCH` to avoid N+1 problems
- `@PreAuthorize` annotations on controller methods are the primary access control — `SecurityConfig` URL rules are a secondary layer
- JPA auditing wired to `SecurityUtils.getCurrentUserId()` for `@CreatedBy`/`@LastModifiedBy` columns

## API Documentation

Swagger UI: `http://localhost:8089/swagger-ui.html` (public, no auth required)
