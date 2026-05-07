# Quickstart — Shift-End Ticket Handover

**Feature**: `001-ticket-handover`
**Audience**: developer picking up `/speckit-tasks` and
`/speckit-implement`.

This walkthrough lets a developer go from clean checkout to
end-to-end demo of the handover flow on `localhost`.

---

## 0. Prerequisites

- **Java 17**, **Maven** (use the wrapper `./mvnw`).
- **PostgreSQL** running on `localhost:5432` with database
  `sageLine_db`, user `postgres`, password `123456`.
- **Keycloak** on `http://localhost:8180`, realm `sageline`,
  client `admin-cli` (admin/admin). At least the roles
  `ADMIN_IT`, `CHEF_SECTEUR`, `TECH_VAL`, `EXPERT` configured.
- **Frontend** Angular dev server on `http://localhost:4200`
  (only origin allowed by CORS).
- Optional: Python ML service on `http://localhost:5000`
  (`AIPredictionService` falls back gracefully).

---

## 1. Run the backend

```bash
./mvnw spring-boot:run     # starts on http://localhost:8089
```

Confirm the new feature loaded:

```bash
curl http://localhost:8089/swagger-ui.html       # should list /api/handovers/*
```

`SagelineApplication` MUST carry `@EnableScheduling`. The cron
`0 45 16 * * MON-FRI` registers automatically once present.

---

## 2. Seed minimal data

Use Swagger UI or any HTTP client. Acquire a Keycloak token for
each role you need:

```bash
TOKEN=$(curl -s -X POST \
  -d "client_id=admin-cli&grant_type=password&username=jean&password=password" \
  http://localhost:8180/realms/sageline/protocol/openid-connect/token \
  | jq -r .access_token)
```

Sync the user into the SageLine DB on first login:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8089/api/users/me
```

Create or pick a `Validation` ticket already in `EN_COURS` with an
`ACTIVE` `ValidationAssignment` for `jean` (use existing
`/api/validations/...` endpoints).

---

## 3. Demo flow A — automated shift-end

Fast-forward instead of waiting for 16:45: temporarily call the
job directly from a `@Profile("dev")` test endpoint, OR change
the cron to `*/30 * * * * MON-FRI` for the demo.

Expected:

- `Validation.status = EN_ATTENTE_HANDOVER`.
- `TicketHandover` row inserted: `status=PENDING`,
  `triggeredBy=SHIFT_END_AUTO`, `fromTech=jean`.
- Jean's `ValidationAssignment.status = PAUSED`.
- STOMP frame on `/user/{jean.id}/queue/handover` and on
  `/topic/handover.zone.{zoneId}`, `type=HANDOVER_TRIGGERED`.
- A `Notification` row exists for Jean.

Re-trigger the job: NO duplicate row, NO duplicate STOMP frames
(scheduler idempotency, R6).

---

## 4. Demo flow B — manual initiate

```bash
curl -X POST -H "Authorization: Bearer $TOKEN_JEAN" \
     -H "Content-Type: application/json" \
     -d '{"handoverNote":"QC step 3/5 done","progressSummary":"Phase A done; phase B halfway"}' \
     http://localhost:8089/api/handovers/initiate/42
```

Same effect as flow A but with `triggeredBy=MANUAL`.

---

## 5. Demo flow C — supervisor designates a tech, then accept

```bash
# CHEF_SECTEUR
curl -X PATCH -H "Authorization: Bearer $TOKEN_CHEF" \
     -H "Content-Type: application/json" \
     -d '{"techId": 88}' \
     http://localhost:8089/api/handovers/17/assign

# Designated TECH_VAL self-confirms
curl -X POST -H "Authorization: Bearer $TOKEN_SANA" \
     http://localhost:8089/api/handovers/17/accept
```

Expected after `/accept`: handover `COMPLETED`, ticket back to
`EN_COURS` under Sana with a NEW `ValidationAssignment.ACTIVE`,
`HANDOVER_ACCEPTED` STOMP frame on the zone topic.

---

## 6. Demo flow D — race-safety check

Open two terminals as two different `TECH_VAL` users in the same
zone, fire both `/accept` calls simultaneously. Expected: one
returns `200`, the other returns `400 ValidationException:
handover already accepted` and the live queue panel removes the
row in both browsers.

---

## 7. Demo flow E — early closure with auto-cancel

While a handover is `PENDING` for ticket 42, have the original
`TECH_VAL` (Jean) close the ticket via the existing
`PATCH /api/validations/42/submit-review`. Expected: the
handover transitions to `CANCELLED` in the same transaction, the
queue panel removes the row, and the ticket reaches `EN_REVUE`.

---

## 8. KPI verification

```bash
curl -H "Authorization: Bearer $TOKEN_CHEF" \
  "http://localhost:8089/api/handovers/kpis?from=2026-04-06&to=2026-05-06"
```

Confirm: `totalCount`, `byTriggerType`, `byZone`,
`byTechnician`, and `timeToAccept.{medianSeconds, p95Seconds}`
all populated.

---

## 9. Acceptance scenarios mapping

| Spec scenario | Demo flow |
|---|---|
| US 1 — Automated shift-end | §3 |
| US 1 #2 — Idempotency | §3 (re-trigger) |
| US 2 — Self-accept | §5 (final step) |
| US 3 — Manual handover | §4 |
| US 4 — Supervisor assigns | §5 |
| US 5 — Forced handover | §4 with `TOKEN_ADMIN` |
| US 6 — Cancellation | `PATCH /api/handovers/{id}/cancel` |
| US 7 — History & KPIs | `GET /api/handovers/validation/{id}` and §8 |
| FR-007a — Race safety | §6 |
| FR-006a — Early closure | §7 |
| FR-019a — Cross-zone block | retry §5 with a `TECH_VAL` in a different zone — expect `400` |
| FR-015a — Notification persistence | inspect `notifications` table after each personal alert |

---

## 10. Rollback

Disable the feature without code rollback:

- Comment out `@EnableScheduling` (kills the auto job).
- Remove handover URL rules from `SecurityConfig` to 403 the
  endpoints.

The data model (`ticket_handovers` table, new enum values) is
backwards-compatible — leaving them in place is safe.
