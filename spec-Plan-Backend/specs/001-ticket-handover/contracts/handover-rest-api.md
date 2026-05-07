# Contract — Handover REST API

**Feature**: Shift-End Ticket Handover (`001-ticket-handover`)
**Base path**: `/api/handovers`
**Auth**: Keycloak JWT bearer; `KeycloakJwtConverter` maps
`realm_access.roles` to `ROLE_<NAME>` authorities.
**Error contract**: `GlobalExceptionHandler` translates
`ResourceNotFoundException → 404` and `ValidationException → 400`.
Authorization failures are `403` from Spring Security.

All bodies are JSON. All timestamps are ISO-8601
`yyyy-MM-dd'T'HH:mm:ss` (server local time).

---

## 1. `POST /api/handovers/initiate/{validationId}`

Manually or forcibly initiate a handover on an in-progress
ticket.

- **Roles**: `TECH_VAL`, `CHEF_SECTEUR`, `ADMIN_IT`
- **`@PreAuthorize`**: `hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')`
- **Path params**: `validationId : Long`
- **Body** (`HandoverInitiateRequest`):

  ```json
  {
    "handoverNote":   "QC step 3 of 5 done — defect under inspection",
    "progressSummary": "Started 14:00. Phase A complete, phase B halfway."
  }
  ```

- **Service-layer rules**:
  - If caller has `TECH_VAL` and IS the current `ACTIVE` assignee →
    `triggeredBy = MANUAL`.
  - Else if caller has `CHEF_SECTEUR` or `ADMIN_IT` →
    `triggeredBy = ADMIN_FORCE`.
  - Else `403`.
  - `validation.status` MUST be `EN_COURS` and there MUST be no
    existing non-terminal `TicketHandover` (idempotency guard).

- **Response** `200 OK` (`HandoverResponse`):

  ```json
  {
    "id": 17,
    "validationId": 42,
    "ticketCode": "VAL-2026-0042",
    "fromTechUsername": "jean",
    "toTechUsername": null,
    "handoverNote": "QC step 3 of 5 done — defect under inspection",
    "progressSummary": "Started 14:00. Phase A complete, phase B halfway.",
    "status": "PENDING",
    "triggeredBy": "MANUAL",
    "scheduledAt": "2026-05-06T15:30:00",
    "acceptedAt": null
  }
  ```

- **Errors**:
  - `404` validation not found
  - `400` ticket not in `EN_COURS`, OR a pending handover already exists, OR no active assignment
  - `403` caller lacks both manual and force eligibility

- **Side effects** (transactional):
  - Ticket → `EN_ATTENTE_HANDOVER`
  - Outgoing assignment → `PAUSED`, `handoverNote` stored
  - Personal `Notification` row + STOMP event for outgoing tech
  - STOMP event on `/topic/handover.zone.{zoneId}`

---

## 2. `POST /api/handovers/{handoverId}/accept`

Current `TECH_VAL` self-accepts a pending handover and resumes
the ticket.

- **Roles**: `TECH_VAL`
- **`@PreAuthorize`**: `hasRole('TECH_VAL')`
- **Path params**: `handoverId : Long`
- **Body**: empty
- **Service-layer rules** (R1, R3):
  - User's `productionLine` MUST equal ticket's `productionLine`,
    else `400 Validation: cross-zone self-accept not allowed`.
  - Handover transition `PENDING → COMPLETED` (or `ACCEPTED →
    COMPLETED` if user equals `toTech`) is performed via
    `compareAndSetStatus(...)`. If 0 rows affected → `400 Validation:
    handover already accepted`.
  - New `ValidationAssignment(status=ACTIVE)` created for the
    accepting user; ticket → `EN_COURS`.

- **Response** `200 OK`: `HandoverResponse` with `status =
  COMPLETED`, `toTech` populated, `acceptedAt` set.

- **Errors**: `404`, `400` (race / cross-zone / wrong status), `403`.

- **Side effects**: STOMP `HANDOVER_ACCEPTED` on
  `/topic/handover.zone.{zoneId}`; queue panel removes the row.

---

## 3. `PATCH /api/handovers/{handoverId}/assign`

Supervisor or admin designates a specific incoming `TECH_VAL`.

- **Roles**: `CHEF_SECTEUR`, `ADMIN_IT`
- **`@PreAuthorize`**: `hasAnyRole('CHEF_SECTEUR','ADMIN_IT')`
- **Path params**: `handoverId : Long`
- **Body** (`HandoverAssignRequest`):

  ```json
  { "techId": 88 }
  ```

- **Service-layer rules**:
  - `techId` MUST resolve to a user currently holding `TECH_VAL`,
    else `400`.
  - Cross-zone assignment IS allowed for this endpoint (this is the
    documented escape hatch for FR-019a).
  - Transition `PENDING → ACCEPTED`, `toTech = user`. Note:
    acceptance still requires the designated tech to call
    `/accept` (transition `ACCEPTED → COMPLETED`).

- **Response** `200 OK`: `HandoverResponse` with `status =
  ACCEPTED`, `toTech` populated, `acceptedAt` still null.

- **Side effects**: persist a `Notification` row for the
  designated tech; STOMP event on `/user/{techId}/queue/handover`
  (`HANDOVER_ASSIGNED`); STOMP event on
  `/topic/handover.zone.{zoneId}`.

---

## 4. `PATCH /api/handovers/{handoverId}/cancel`

Cancel a non-terminal handover. Restores the ticket and
reactivates the original assignment.

- **Roles**: `CHEF_SECTEUR`, `ADMIN_IT`
- **`@PreAuthorize`**: `hasAnyRole('CHEF_SECTEUR','ADMIN_IT')`
- **Service-layer rules**: handover MUST be in `PENDING` or
  `ACCEPTED`; else `400`.
- **Response** `200 OK`: `HandoverResponse` with `status =
  CANCELLED`.
- **Side effects**:
  - Ticket → `EN_COURS`
  - Original `PAUSED` assignment → `ACTIVE`
  - STOMP event `HANDOVER_CANCELLED` on
    `/topic/handover.zone.{zoneId}` and on
    `/user/{originalTechId}/queue/handover`

---

## 5. `GET /api/handovers/pending`

List all pending handovers across zones (queue panel).

- **Roles**: `CHEF_SECTEUR`, `ADMIN_IT`
- **`@PreAuthorize`**: `hasAnyRole('CHEF_SECTEUR','ADMIN_IT')`
- **Response** `200 OK`: `HandoverResponse[]`, ordered by
  `scheduledAt ASC`.
- **Note**: A future enhancement MAY filter by the caller's zone
  for `CHEF_SECTEUR`. For v1, the supervisor sees all zones; the
  Angular client filters client-side (see `Plan.md` §Phase 4).

---

## 6. `GET /api/handovers/validation/{validationId}`

Full chronological handover history for a ticket.

- **Roles**: any authenticated role
- **`@PreAuthorize`**: `isAuthenticated()`
- **Response** `200 OK`: `HandoverResponse[]`, ordered by
  `scheduledAt ASC`. Empty array if none.

---

## 7. `GET /api/handovers/kpis`

Handover KPI snapshot for a date range.

- **Roles**: `CHEF_SECTEUR`, `EXPERT`, `ADMIN_IT`
- **`@PreAuthorize`**: `hasAnyRole('CHEF_SECTEUR','EXPERT','ADMIN_IT')`
- **Query params**:
  - `from : LocalDate` (inclusive)
  - `to   : LocalDate` (inclusive; converted to end-of-day server-side)
- **Response** `200 OK` (`HandoverKpiResponse`):

  ```json
  {
    "from": "2026-04-06",
    "to":   "2026-05-06",
    "totalCount": 47,
    "byTriggerType": {
      "MANUAL":         12,
      "SHIFT_END_AUTO": 30,
      "ADMIN_FORCE":     5
    },
    "byZone": [
      { "zoneId": 1, "zoneName": "Ligne A", "count": 18 },
      { "zoneId": 2, "zoneName": "Ligne B", "count": 29 }
    ],
    "byTechnician": [
      { "userId": 7, "username": "jean",    "count": 9 },
      { "userId": 8, "username": "mohamed", "count": 6 }
    ],
    "timeToAccept": {
      "sampleSize": 41,
      "medianSeconds": 312,
      "p95Seconds":   1180
    }
  }
  ```

- **Computed only over `status = COMPLETED`** for the
  `timeToAccept` bucket; counts include all statuses except
  `CANCELLED` (cancelled rows are excluded from frequency
  metrics — they did not produce a handover).

---

## DTOs (Java)

```java
public record HandoverInitiateRequest(
    String handoverNote,
    String progressSummary
) {}

public record HandoverAssignRequest(Long techId) {}

public record HandoverResponse(
    Long id,
    Long validationId,
    String ticketCode,
    String fromTechUsername,
    String toTechUsername,
    String handoverNote,
    String progressSummary,
    HandoverStatus status,
    TriggerType triggeredBy,
    LocalDateTime scheduledAt,
    LocalDateTime acceptedAt
) {}

public record HandoverKpiResponse(
    LocalDate from,
    LocalDate to,
    long totalCount,
    Map<TriggerType, Long> byTriggerType,
    List<ZoneCount> byZone,
    List<TechnicianCount> byTechnician,
    TimeToAcceptStats timeToAccept
) {
    public record ZoneCount(Long zoneId, String zoneName, long count) {}
    public record TechnicianCount(Long userId, String username, long count) {}
    public record TimeToAcceptStats(long sampleSize, long medianSeconds, long p95Seconds) {}
}
```

---

## Authorization summary (single source of truth)

| Endpoint | Roles allowed |
|---|---|
| `POST /initiate/{id}` | `TECH_VAL` (manual) / `CHEF_SECTEUR`, `ADMIN_IT` (force) |
| `POST /{id}/accept`   | `TECH_VAL` (same-zone — service guard) |
| `PATCH /{id}/assign`  | `CHEF_SECTEUR`, `ADMIN_IT` |
| `PATCH /{id}/cancel`  | `CHEF_SECTEUR`, `ADMIN_IT` |
| `GET /pending`        | `CHEF_SECTEUR`, `ADMIN_IT` |
| `GET /validation/{id}`| any authenticated |
| `GET /kpis`           | `CHEF_SECTEUR`, `EXPERT`, `ADMIN_IT` |

`SecurityConfig` URL rules MUST mirror this table as
defence-in-depth (Constitution Principle II).
