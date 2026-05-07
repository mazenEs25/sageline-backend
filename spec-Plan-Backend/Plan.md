# SageLine — Handover System Specification

> **Feature:** Shift-End Ticket Handover  
> **Project:** SageLine (PFE — Esprit / Sagem)  
> **Method:** Spec-Driven Development (Spec Kit)  
> **Status:** Ready for implementation  
> **Date:** May 2026

---

## Executive Summary

At Sagem, the workday ends at **17:00**. A `TECH_VAL` may be actively working on a validation ticket when their shift ends, leaving the ticket frozen in `EN_COURS` with no one accountable for continuing it. There is currently no formal mechanism to transfer ownership of an in-progress ticket to another available technician.

The **Handover System** solves this by introducing a structured transfer protocol with three trigger paths:

| Trigger | Who | When |
|---|---|---|
| **Automated** | System scheduler | Every weekday at 16:45 — scans all `EN_COURS` tickets |
| **Manual** | `TECH_VAL` | Technician voluntarily initiates before shift end |
| **Forced** | `ADMIN_IT` / `CHEF_SECTEUR` | Override at any time |

When a handover is triggered, the ticket transitions to `EN_ATTENTE_HANDOVER`, the outgoing technician's session is paused, a handover note is recorded, and the supervisor is alerted in real time via WebSocket. Once a new `TECH_VAL` accepts the ticket, they receive the full progress context and resume work seamlessly.

### Key outcomes for the PFE defense

- Demonstrates **event-driven scheduling** (`@Scheduled` cron + Spring) integrated with the existing validation workflow
- Extends the **state machine** with a new status and clean lifecycle transitions
- Adds **real-time supervisor tooling** — a live queue panel driven by WebSocket zone topics
- Preserves **full audit history** — every handover (who, when, why, what progress) is recorded
- Feeds **KPI dashboard** with handover frequency metrics per zone, technician, and time period

---

## Phase 1 — Specification: Feature Brief

### Problem Statement

Tickets left in `EN_COURS` at shift end have no owner for the next session. Supervisors have no visibility into which tickets are blocked. Technicians starting the next day have no context on what was done. The validation chain breaks.

### Actors

| Actor | Role in Handover |
|---|---|
| `TECH_VAL` (outgoing) | Initiates manual handover; receives automated alert at 16:45; writes progress note |
| `TECH_VAL` (incoming) | Accepts or is assigned the handover; reads progress context before resuming |
| `CHEF_SECTEUR` | Assigns a specific technician when no one self-assigns; monitors zone queue in real time |
| `ADMIN_IT` | Full override — can force-handover any ticket at any time |
| Scheduler (system) | Automated job at 16:45 — scans and triggers handovers without human action |

### Goals

1. Detect all tickets `EN_COURS` at **16:45** and alert assigned `TECH_VAL` and their `CHEF_SECTEUR`
2. Allow a `TECH_VAL` to initiate a **voluntary handover** before shift end, with a progress note
3. Allow `CHEF_SECTEUR` / `ADMIN_IT` to **assign the ticket** to a new `TECH_VAL` and resume next session
4. Preserve **full traceability** — who held the ticket, when, for how long, and what was accomplished
5. Resume the ticket **seamlessly** — the new technician sees the full progress summary before starting
6. Expose **KPI metrics** on handover frequency per zone, technician, and time period

### Out of Scope

- Cross-shift scheduling (night shift, weekend coverage planning)
- SLA breach auto-escalation beyond notification
- Reassignment of `TECH_PREP` prep-phase tickets
- Multi-step approval chains for handover acceptance

---

## Phase 2 — Data Model: Entities & State Machine

### New Entity — `TicketHandover`

```java
@Entity
@Table(name = "ticket_handovers")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketHandover {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_tech_id", nullable = false)
    private User fromTech;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_tech_id")
    private User toTech;                         // nullable until assigned/accepted

    @Column(columnDefinition = "TEXT")
    private String handoverNote;

    @Column(columnDefinition = "TEXT")
    private String progressSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HandoverStatus status;               // PENDING → ACCEPTED → COMPLETED | CANCELLED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggeredBy;             // MANUAL | SHIFT_END_AUTO | ADMIN_FORCE

    @Column(nullable = false)
    private LocalDateTime scheduledAt;           // when the handover was created

    private LocalDateTime acceptedAt;            // when a new tech took over
}
```

### New Enums

```java
// HandoverStatus.java
public enum HandoverStatus {
    PENDING,      // Awaiting assignment or acceptance
    ACCEPTED,     // A TECH_VAL was designated — not yet confirmed
    COMPLETED,    // New tech is active, ticket resumed
    CANCELLED     // Supervisor cancelled (e.g. ticket resolved before handover)
}

// TriggerType.java
public enum TriggerType {
    MANUAL,           // Tech voluntarily initiated
    SHIFT_END_AUTO,   // Scheduler triggered at 16:45
    ADMIN_FORCE       // CHEF_SECTEUR or ADMIN_IT forced override
}
```

### Extended `TicketStatus` Enum

Add `EN_ATTENTE_HANDOVER` to the existing `TicketStatus` enum:

```
PLANIFIE → EN_PREP → PRET → EN_COURS → EN_ATTENTE_HANDOVER → EN_COURS (new tech)
                                                            ↘ EN_REVUE (if skipped)
```

The ticket is **frozen** in `EN_ATTENTE_HANDOVER` — no validation results can be submitted until a new tech accepts.

### Extended `AssignmentStatus` Enum

Add `PAUSED` to the existing `AssignmentStatus` enum:

| Status | Meaning |
|---|---|
| `ACTIVE` | Technician is currently working the ticket |
| `PAUSED` | Technician's session ended via handover — assignment preserved for audit |
| `COMPLETED` | Technician finished and submitted |
| `CANCELLED` | Assignment removed |

### `ValidationAssignment` — new field

Add `handoverNote: String` (columnDefinition = "TEXT") to record what the outgoing tech accomplished before the handover.

### Entity Relationship Summary

```
Validation (1) ─────────── (N) TicketHandover
Validation (1) ─────────── (N) ValidationAssignment
User (1) ──── fromTech ─── (N) TicketHandover
User (1) ──── toTech   ─── (N) TicketHandover
```

---

## Phase 3 — Backend Plan: APIs, Scheduler, WebSocket

### REST Endpoints — `HandoverController`

Base path: `/api/handovers`

| Method | Path | Role | Description |
|---|---|---|---|
| `POST` | `/initiate/{validationId}` | `TECH_VAL`, `ADMIN_IT` | Manually initiate a handover. Body: `{ handoverNote, progressSummary }` |
| `POST` | `/{handoverId}/accept` | `TECH_VAL` | Current user self-assigns and accepts the handover |
| `PATCH` | `/{handoverId}/assign` | `CHEF_SECTEUR`, `ADMIN_IT` | Designate a specific `TECH_VAL` as the new owner. Body: `{ techId }` |
| `PATCH` | `/{handoverId}/cancel` | `CHEF_SECTEUR`, `ADMIN_IT` | Cancel a pending handover |
| `GET` | `/pending` | `CHEF_SECTEUR`, `ADMIN_IT` | List all `PENDING` handovers across all zones |
| `GET` | `/validation/{validationId}` | All roles | Full handover history for a specific ticket |
| `GET` | `/kpis` | `CHEF_SECTEUR`, `EXPERT`, `ADMIN_IT` | Handover frequency stats by zone / tech / period |

### Request / Response DTOs

```java
// HandoverInitiateRequest.java
public record HandoverInitiateRequest(
    String handoverNote,
    String progressSummary
) {}

// HandoverAssignRequest.java
public record HandoverAssignRequest(Long techId) {}

// HandoverResponse.java
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
```

### Scheduler — `ShiftEndHandoverJob`

```java
@Component
@RequiredArgsConstructor
public class ShiftEndHandoverJob {

    private final ValidationRepository validationRepository;
    private final HandoverService handoverService;

    // Fires at 16:45 every Monday–Friday
    @Scheduled(cron = "0 45 16 * * MON-FRI")
    public void triggerShiftEndHandovers() {
        List<Validation> activeTickets = validationRepository
            .findByStatusWithActiveAssignment(TicketStatus.EN_COURS);

        for (Validation ticket : activeTickets) {
            if (ticket.hasNoPendingHandover()) {
                handoverService.triggerAutoHandover(ticket);
            }
        }
    }
}
```

**Job behavior:**
1. Queries all `Validation` with status `EN_COURS` and at least one `ACTIVE` assignment
2. For each ticket: creates `TicketHandover` (status=`PENDING`, trigger=`SHIFT_END_AUTO`)
3. Sets ticket status → `EN_ATTENTE_HANDOVER`
4. Sets outgoing assignment → `PAUSED`, stores progress summary if tech provided one
5. Sends WebSocket notification to assigned `TECH_VAL`: *"Votre shift se termine à 17h00. Veuillez compléter votre note de passation."*
6. Sends WebSocket notification to `CHEF_SECTEUR` of the zone: *"Le ticket [code] nécessite une passation."*
7. **Idempotent** — skips tickets already in `EN_ATTENTE_HANDOVER`

Enable scheduling in main class: `@EnableScheduling`

### `HandoverService` — Operation Contract

```java
public interface HandoverService {

    // Manual or auto initiation — creates TicketHandover + transitions ticket status
    HandoverResponse initiateHandover(Long validationId, HandoverInitiateRequest req, TriggerType trigger);

    // Automated path (called by scheduler)
    void triggerAutoHandover(Validation ticket);

    // New TECH_VAL self-accepts — creates new ValidationAssignment, resumes ticket
    HandoverResponse acceptHandover(Long handoverId);

    // CHEF_SECTEUR designates a specific tech — sends personal WS notification
    HandoverResponse assignHandover(Long handoverId, Long techId);

    // Cancel a pending handover and restore ticket to EN_COURS
    HandoverResponse cancelHandover(Long handoverId);

    // History for ticket detail view
    List<HandoverResponse> getHandoverHistory(Long validationId);

    // KPI data for dashboard
    HandoverKpiResponse getHandoverKpis(LocalDate from, LocalDate to);
}
```

All methods are `@Transactional`. Each state-changing operation emits a WebSocket event via `SimpMessagingTemplate`.

### WebSocket Events

| Topic | Audience | Payload |
|---|---|---|
| `/user/{userId}/queue/handover` | Personal — outgoing `TECH_VAL` and assigned incoming `TECH_VAL` | `HandoverNotificationDto` |
| `/topic/handover.zone.{zoneId}` | `CHEF_SECTEUR` of the zone — all pending handovers | `HandoverNotificationDto` |

```java
// HandoverNotificationDto.java
public record HandoverNotificationDto(
    String type,              // "HANDOVER_TRIGGERED" | "HANDOVER_ASSIGNED" | "HANDOVER_COMPLETED"
    Long handoverId,
    Long validationId,
    String ticketCode,
    String message,
    LocalDateTime timestamp
) {}
```

### Custom Repository Query

```java
// ValidationRepository.java
@Query("""
    SELECT DISTINCT v FROM Validation v
    LEFT JOIN FETCH v.assignments a
    LEFT JOIN FETCH a.user
    WHERE v.status = :status
    AND a.status = 'ACTIVE'
""")
List<Validation> findByStatusWithActiveAssignment(@Param("status") TicketStatus status);
```

---

## Phase 4 — Frontend Plan: Angular Components & UX

### New Components

All components go under `src/app/pages/Handover/` following existing project structure.

#### `HandoverInitiateDialogComponent`

- **Trigger:** Banner warning shown to `TECH_VAL` at 16:45 on any ticket detail page with status `EN_COURS`, OR a manual "Initier la passation" button
- **UI:** PrimeNG `p-dialog` with two `p-inputTextarea` fields — progress summary and handover note
- **Action:** Calls `HandoverService.initiateHandover()` on submit
- **Post-submit:** Dialog closes, ticket status refreshes to `EN_ATTENTE_HANDOVER`, HandoverBanner appears

#### `HandoverBannerComponent`

- **Used in:** `ticket-detail` page, injected when `ticket.status === 'EN_ATTENTE_HANDOVER'`
- **UI:** PrimeNG `p-message` with severity `warn` — amber banner
- **Content (role-aware):**
    - `TECH_VAL`: *"Ce ticket est en attente de passation."* + "Accepter" button
    - `CHEF_SECTEUR` / `ADMIN_IT`: *"Passation en attente — assigner un technicien"* + dropdown of available `TECH_VAL` users

#### `HandoverAcceptPanelComponent`

- **Route:** `/validations/:id/handover`
- **Roles:** `TECH_VAL`, `CHEF_SECTEUR`, `ADMIN_IT`
- **UI:** Card showing: previous tech name, handover note, progress summary, creation datetime
- **Action:** "Accepter la passation" button → calls `HandoverService.acceptHandover()`
- **Post-accept:** Redirects to `/validations/:id` with ticket now `EN_COURS` under new owner

#### `HandoverQueuePanelComponent`

- **Route:** `/handovers/queue`
- **Roles:** `CHEF_SECTEUR`, `ADMIN_IT`
- **UI:** PrimeNG `p-table` listing all `PENDING` handovers with columns: ticket code, zone, outgoing tech, created at, action
- **Live update:** Subscribed to `/topic/handover.zone.{zoneId}` — new rows appear in real time via WebSocket without page refresh
- **Actions per row:** "Assigner" (opens a user picker dropdown) + "Annuler"
- **Sidebar entry:** Add *"Passations"* with `pi pi-arrows-h` icon and badge counter for `CHEF_SECTEUR`

#### `HandoverTimelineComponent`

- **Used in:** `ticket-detail` page, below the existing `TicketTimeline`
- **UI:** PrimeNG `p-timeline` — each entry shows: from/to tech, trigger type badge, handover note, datetime
- **Data:** Calls `HandoverService.getHandoverHistory(validationId)` on init
- **Only renders** if `handovers.length > 0`

### Angular Service

```typescript
// src/app/services/handover.service.ts

@Injectable({ providedIn: 'root' })
export class HandoverService {

  private apiUrl = `${environment.apiUrl}/handovers`;

  constructor(private http: HttpClient) {}

  initiateHandover(validationId: number, body: HandoverInitiateRequest): Observable<HandoverResponse> {
    return this.http.post<HandoverResponse>(`${this.apiUrl}/initiate/${validationId}`, body);
  }

  acceptHandover(handoverId: number): Observable<HandoverResponse> {
    return this.http.post<HandoverResponse>(`${this.apiUrl}/${handoverId}/accept`, {});
  }

  assignHandover(handoverId: number, techId: number): Observable<HandoverResponse> {
    return this.http.patch<HandoverResponse>(`${this.apiUrl}/${handoverId}/assign`, { techId });
  }

  cancelHandover(handoverId: number): Observable<HandoverResponse> {
    return this.http.patch<HandoverResponse>(`${this.apiUrl}/${handoverId}/cancel`, {});
  }

  getPendingHandovers(): Observable<HandoverResponse[]> {
    return this.http.get<HandoverResponse[]>(`${this.apiUrl}/pending`);
  }

  getHandoverHistory(validationId: number): Observable<HandoverResponse[]> {
    return this.http.get<HandoverResponse[]>(`${this.apiUrl}/validation/${validationId}`);
  }

  getHandoverKpis(from: string, to: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/kpis`, { params: { from, to } });
  }
}
```

### Model & Enum Files

```typescript
// src/app/models/handover.model.ts
export interface HandoverResponse {
  id: number;
  validationId: number;
  ticketCode: string;
  fromTechUsername: string;
  toTechUsername?: string;
  handoverNote: string;
  progressSummary: string;
  status: HandoverStatus;
  triggeredBy: TriggerType;
  scheduledAt: string;
  acceptedAt?: string;
}

export interface HandoverInitiateRequest {
  handoverNote: string;
  progressSummary: string;
}

// src/app/shared/enums/handover-status.enum.ts
export enum HandoverStatus {
  PENDING    = 'PENDING',
  ACCEPTED   = 'ACCEPTED',
  COMPLETED  = 'COMPLETED',
  CANCELLED  = 'CANCELLED'
}

export const HANDOVER_STATUS_LABELS: Record<HandoverStatus, string> = {
  [HandoverStatus.PENDING]:   'En attente',
  [HandoverStatus.ACCEPTED]:  'Assignée',
  [HandoverStatus.COMPLETED]: 'Terminée',
  [HandoverStatus.CANCELLED]: 'Annulée'
};

export const HANDOVER_STATUS_SEVERITY: Record<HandoverStatus, string> = {
  [HandoverStatus.PENDING]:   'warning',
  [HandoverStatus.ACCEPTED]:  'info',
  [HandoverStatus.COMPLETED]: 'success',
  [HandoverStatus.CANCELLED]: 'danger'
};

// src/app/shared/enums/trigger-type.enum.ts
export enum TriggerType {
  MANUAL         = 'MANUAL',
  SHIFT_END_AUTO = 'SHIFT_END_AUTO',
  ADMIN_FORCE    = 'ADMIN_FORCE'
}

export const TRIGGER_TYPE_LABELS: Record<TriggerType, string> = {
  [TriggerType.MANUAL]:         'Manuelle',
  [TriggerType.SHIFT_END_AUTO]: 'Automatique (fin de shift)',
  [TriggerType.ADMIN_FORCE]:    'Forcée (admin)'
};
```

### Route Additions

```typescript
// app-routing.module.ts
{
  path: 'validations/:id/handover',
  component: HandoverAcceptPanelComponent,
  canActivate: [AuthGuard],
  data: { roles: ['TECH_VAL', 'CHEF_SECTEUR', 'ADMIN_IT'] }
},
{
  path: 'handovers/queue',
  component: HandoverQueuePanelComponent,
  canActivate: [AuthGuard],
  datayes: { roles: ['CHEF_SECTEUR', 'ADMIN_IT'] }
}
```

### WebSocket Subscription Wiring

In `AppComponent.ngOnInit()`, after `wsService.connect(userId)`:

```typescript
// Subscribe to personal handover alerts
this.wsService.subscribe(`/user/${userId}/queue/handover`, (msg) => {
  const notification = JSON.parse(msg.body);
  this.notificationService.showHandoverAlert(notification);
});

// CHEF_SECTEUR only — subscribe to zone topic
if (this.authService.hasRole('CHEF_SECTEUR')) {
  const zoneId = this.authService.getUserZoneId();
  this.wsService.subscribe(`/topic/handover.zone.${zoneId}`, (msg) => {
    this.handoverQueueService.refresh();
  });
}
```

---

## Phase 5 — Acceptance Criteria & Task Breakdown

### Acceptance Criteria (BDD)

#### Scenario 1 — Automated shift-end handover

```
Given  A ticket VAL-2026-0042 is EN_COURS at 16:45
       And TECH_VAL Jean is ACTIVE on the assignment
When   ShiftEndHandoverJob fires (cron: 0 45 16 * * MON-FRI)
Then   Ticket status → EN_ATTENTE_HANDOVER
       And TicketHandover created: status=PENDING, trigger=SHIFT_END_AUTO
       And Jean's ValidationAssignment status → PAUSED
       And Jean receives WebSocket notification on /user/{id}/queue/handover
       And CHEF_SECTEUR receives alert on /topic/handover.zone.{zoneId}
       And HandoverBanner appears on ticket detail for all users
```

#### Scenario 2 — New tech self-accepts handover

```
Given  Handover for VAL-2026-0042 is PENDING
When   TECH_VAL Mohamed visits /validations/42/handover and clicks "Accepter"
Then   Handover status → COMPLETED
       And Ticket status → EN_COURS
       And New ValidationAssignment created for Mohamed: status=ACTIVE
       And Mohamed sees Jean's progressSummary on the accept panel
       And HandoverTimeline shows the handover entry in ticket detail
       And acceptedAt timestamp is set on the TicketHandover record
```

#### Scenario 3 — Voluntary manual handover

```
Given  TECH_VAL Jean is working ticket VAL-2026-0055 (EN_COURS) at 15:30
When   Jean clicks "Initier la passation" and fills the handover note
Then   Same outcome as Scenario 1 but triggeredBy = MANUAL
       And CHEF_SECTEUR sees the ticket appear in HandoverQueuePanel immediately
```

#### Scenario 4 — Supervisor assigns a specific technician

```
Given  Handover for VAL-2026-0042 is PENDING with no toTech
When   CHEF_SECTEUR assigns TECH_VAL Sana from HandoverQueuePanel
Then   Handover.toTech = Sana, status → ACCEPTED
       And Sana receives personal WebSocket notification on /user/{id}/queue/handover
       And Sana can accept from /validations/42/handover
```

#### Scenario 5 — Idempotency guard

```
Given  ShiftEndHandoverJob already created a handover for VAL-2026-0042
When   Job fires again (e.g. restarted server, duplicate trigger)
Then   No second TicketHandover is created
       And No duplicate WebSocket notifications are sent
```

### Implementation Task Order

| # | Task | Layer | Depends on |
|---|---|---|---|
| 1 | Add `EN_ATTENTE_HANDOVER` to `TicketStatus`, `PAUSED` to `AssignmentStatus`, `handoverNote` field to `ValidationAssignment` | Entity / Enum | — |
| 2 | Create `TicketHandover` entity, `HandoverStatus` enum, `TriggerType` enum, `HandoverRepository` | Entity / Repository | Task 1 |
| 3 | Implement `HandoverService` — all 6 operations, `@Transactional`, WebSocket events | Service | Task 2 |
| 4 | Implement `ShiftEndHandoverJob` with `@Scheduled` cron, idempotency check | Scheduler | Task 3 |
| 5 | Implement `HandoverController` with all 7 endpoints, `@PreAuthorize` role gates, request/response DTOs | Controller | Task 3 |
| 6 | Create Angular `HandoverService`, `handover.model.ts`, enums (`HandoverStatus`, `TriggerType`) | Angular Service | Task 5 |
| 7 | Build `HandoverBannerComponent` + integrate into `ticket-detail` | Angular Component | Task 6 |
| 8 | Build `HandoverInitiateDialogComponent` | Angular Component | Task 6 |
| 9 | Build `HandoverAcceptPanelComponent` + route `/validations/:id/handover` | Angular Component | Task 6 |
| 10 | Build `HandoverQueuePanelComponent` + route `/handovers/queue` + sidebar entry | Angular Component | Task 6 |
| 11 | Build `HandoverTimelineComponent` + integrate into `ticket-detail` | Angular Component | Task 6 |
| 12 | Wire WebSocket subscriptions in `AppComponent` for handover topics | WebSocket | Task 3, Task 6 |
| 13 | Add handover KPI metrics to `KpiService`, expose `/api/handovers/kpis`, render chart in KPI dashboard | KPI / Dashboard | Task 3 |

---

## Architecture Impact Summary

### Files to create (backend)

```
src/main/java/com/pfe/sageline/
├── entity/
│   └── TicketHandover.java
├── enums/
│   ├── HandoverStatus.java
│   └── TriggerType.java
├── repository/
│   └── HandoverRepository.java
├── service/
│   ├── HandoverService.java            (interface)
│   └── impl/HandoverServiceImpl.java
├── controller/
│   └── HandoverController.java
├── dtos/
│   ├── request/HandoverInitiateRequest.java
│   ├── request/HandoverAssignRequest.java
│   └── response/HandoverResponse.java
├── scheduler/
│   └── ShiftEndHandoverJob.java
└── mappers/
    └── HandoverMapper.java
```

### Files to create (frontend)

```
src/app/
├── pages/Handover/
│   ├── handover-initiate-dialog/
│   ├── handover-accept-panel/
│   ├── handover-queue-panel/
│   └── handover-timeline/
├── services/
│   └── handover.service.ts
├── models/
│   └── handover.model.ts
└── shared/enums/
    ├── handover-status.enum.ts
    └── trigger-type.enum.ts
```

### Files to modify (backend)

- `TicketStatus.java` — add `EN_ATTENTE_HANDOVER`
- `AssignmentStatus.java` — add `PAUSED`
- `ValidationAssignment.java` — add `handoverNote` field
- `ValidationRepository.java` — add `findByStatusWithActiveAssignment` query
- `SagelineApplication.java` — add `@EnableScheduling`
- `SecurityConfig.java` — add handover endpoint role rules

### Files to modify (frontend)

- `app-routing.module.ts` — add 2 new routes
- `app.module.ts` — declare 4 new components
- `ticket-detail.component.html` — inject `HandoverBannerComponent` and `HandoverTimelineComponent`
- `app.component.ts` — add WebSocket subscription logic
- `sidebar` config — add *"Passations"* entry for `CHEF_SECTEUR`
  - `ticket-status.enum.ts` — add `EN_ATTENTE_HANDOVER` + labels/colors