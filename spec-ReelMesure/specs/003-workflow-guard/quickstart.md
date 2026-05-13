# Quickstart: Workflow Guard

End-to-end walk-through. Reproduces Story 1 + Story 2 + Story 3 in one sitting. Assumes a running SageLine backend (port 8089), Keycloak (8180), a `TECH_VAL` user JWT in `$TOKEN`, and a ticket id `42` already in `EN_COURS` whose zone catalog defines 16 mandatory measures.

## 1. Probe the ticket — expect "blocked, 14/16"

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8089/api/validations/42/readiness | jq
```

Expected (status 200):

```json
{
  "ticketId": 42,
  "currentStatus": "EN_COURS",
  "targetStatus": "EN_REVUE",
  "mandatoryTotal": 16,
  "mandatoryFilled": 14,
  "mandatoryMissing": 2,
  "missingMeasures": [
    { "measureCode": "POWER_RMS_AVG_VSA1_ANT3_5670", "label": "...", "required": true },
    { "measureCode": "POWER_RMS_AVG_VSA1_ANT4_5670", "label": "...", "required": true }
  ],
  "outOfRangeMeasures": [],
  "canTransition": false,
  "blockingReasons": ["2 mandatory measures still in NOT_EXECUTED state"]
}
```

## 2. Try to submit-for-review — expect HTTP 422 with the same payload

```bash
curl -s -o /tmp/r.json -w "%{http_code}\n" \
  -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8089/api/validations/42/submit-review
# 422
jq . /tmp/r.json
# same WorkflowReadinessDTO as in step 1
```

## 3. Subscribe to the readiness STOMP topic (separate terminal)

Using `wscat` with a STOMP frame, or any STOMP client. Topic: `/topic/validation.42.readiness`. Leave it running.

## 4. Record one of the missing measures

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"measuredValue": 15.2}' \
  http://localhost:8089/api/validations/42/measures/{measureId}
```

Expect: HTTP 200 from the measure update **and** one new message on the STOMP topic showing `mandatoryFilled: 15`.

## 5. Record the last missing measure → expect `canTransition: true`

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"measuredValue": 14.8}' \
  http://localhost:8089/api/validations/42/measures/{otherMeasureId}
```

STOMP push:

```json
{
  "ticketId": 42,
  "mandatoryTotal": 16, "mandatoryFilled": 16, "mandatoryMissing": 0,
  "missingMeasures": [],
  "canTransition": true,
  "blockingReasons": []
}
```

## 6. Submit-for-review — expect HTTP 200

```bash
curl -s -o /tmp/v.json -w "%{http_code}\n" \
  -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8089/api/validations/42/submit-review
# 200
jq '.status' /tmp/v.json
# "EN_REVUE"
```

## 7. (Optional) Probe the now-EN_REVUE ticket

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8089/api/validations/42/readiness | jq
```

Expected: HTTP 200, `currentStatus: "EN_REVUE"`, `canTransition: false`, `blockingReasons` cites the source-status mismatch (R-007). The probe stays useful for any ticket state.

---

End-to-end walk takes ≤ 2 minutes once a `EN_COURS` ticket with measures exists. Acceptance reviewers can replay it from a clean DB by running the Phase 002 quickstart first to seed the ticket and instantiate measures from the catalog.
