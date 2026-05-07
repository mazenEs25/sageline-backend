# Contract — Handover WebSocket Events

**Feature**: Shift-End Ticket Handover (`001-ticket-handover`)
**STOMP endpoint**: `/ws` (existing, JWT-aware via
`WebSocketEventListener`)
**App prefix**: `/app` · **Topic prefix**: `/topic` · **User
queue prefix**: `/user/{userId}/queue`

All payloads are typed `HandoverNotificationDto`. Constitution
Principle IV forbids untyped maps on the wire.

---

## 1. Topics

| Topic | Audience | Persistence policy |
|---|---|---|
| `/user/{userId}/queue/handover` | Personal — sent to the outgoing `TECH_VAL` and to a designated incoming `TECH_VAL`. | A `Notification` row is **persisted** before each emission (FR-015a). |
| `/topic/handover.zone.{zoneId}` | All `CHEF_SECTEUR` subscribers of the zone. Drives the live queue panel. | Live-only — no `Notification` row created. |

`zoneId` = the production-line ID associated with the ticket.

---

## 2. Payload — `HandoverNotificationDto`

```java
public record HandoverNotificationDto(
    String type,                   // see "Event types" below
    Long handoverId,
    Long validationId,
    String ticketCode,
    String message,                // user-facing French copy
    LocalDateTime timestamp
) {}
```

Example (auto-trigger):

```json
{
  "type": "HANDOVER_TRIGGERED",
  "handoverId": 17,
  "validationId": 42,
  "ticketCode": "VAL-2026-0042",
  "message": "Votre shift se termine à 17h00. Veuillez compléter votre note de passation.",
  "timestamp": "2026-05-06T16:45:00"
}
```

---

## 3. Event types

| `type` | Emitted on | Personal (queue) | Zone (topic) | Triggers `Notification` row |
|---|---|---|---|---|
| `HANDOVER_TRIGGERED` | Initiation by any path (manual / auto / force) | Outgoing `TECH_VAL` | YES | YES (outgoing tech) |
| `HANDOVER_ASSIGNED`  | Supervisor designates a recipient | Designated incoming `TECH_VAL` | YES | YES (designated tech) |
| `HANDOVER_ACCEPTED`  | New tech self-accepts (or designated tech accepts) | — (the accepter is the actor) | YES | NO |
| `HANDOVER_CANCELLED` | Supervisor cancel **or** auto-cancel on early closure (FR-006a) | Original `TECH_VAL` (so they know their reactivated assignment is back live) | YES | YES (original tech) |

Personal column `—` means no personal queue emission for that event.

---

## 4. Message copy (French — v1 inline strings)

| Event | Personal message | Zone message |
|---|---|---|
| `HANDOVER_TRIGGERED` (outgoing tech, auto) | "Votre shift se termine à 17h00. Veuillez compléter votre note de passation pour le ticket {code}." | "Le ticket {code} nécessite une passation." |
| `HANDOVER_TRIGGERED` (outgoing tech, manual) | "Passation initiée pour le ticket {code}." | "Le ticket {code} nécessite une passation." |
| `HANDOVER_TRIGGERED` (outgoing tech, force) | "Une passation a été forcée par l'administration pour le ticket {code}." | "Le ticket {code} nécessite une passation (forcée)." |
| `HANDOVER_ASSIGNED` (designated tech) | "Le ticket {code} vous a été assigné en passation." | "Le ticket {code} a été assigné à {username}." |
| `HANDOVER_ACCEPTED` | — | "Le ticket {code} a été repris par {username}." |
| `HANDOVER_CANCELLED` (original tech) | "La passation pour {code} a été annulée — vous êtes à nouveau l'assigné actif." | "Passation annulée pour {code}." |

i18n / resource-bundle extraction is deferred (see research R10
deferred items).

---

## 5. Subscription contract (frontend reference)

The Angular client subscribes after `wsService.connect(userId)`:

```typescript
// Personal alerts — every authenticated user
wsService.subscribe(`/user/${userId}/queue/handover`, msg => { ... });

// Zone alerts — only CHEF_SECTEUR (and ADMIN_IT for monitoring)
if (authService.hasRole('CHEF_SECTEUR') || authService.hasRole('ADMIN_IT')) {
  const zoneId = authService.getUserZoneId();
  wsService.subscribe(`/topic/handover.zone.${zoneId}`, msg => { ... });
}
```

The backend is the single source of truth for which audience
gets which event — the frontend simply renders what arrives.

---

## 6. Ordering & delivery semantics

- **At-most-once delivery** for live STOMP events (consistent
  with Spring's default broker). Recipients who were offline
  rely on `Notification` rows + the live queue panel reload on
  next connection (per R4).
- **No event ordering guarantee across topics** — clients MUST
  treat each event as a self-contained command and re-fetch the
  ticket / queue if state diverges.
- **Personal queue events are emitted AFTER the database
  transaction commits** to avoid sending notifications for
  rolled-back state. Zone events follow the same rule.

This is enforced by emitting from `HandoverServiceImpl` after the
`@Transactional` boundary returns successfully — typically via
`TransactionSynchronizationManager.registerSynchronization` with
an `afterCommit` callback, or by emitting at the top of the
controller after the service call returns. The implementation
choice is left to `tasks.md`.
