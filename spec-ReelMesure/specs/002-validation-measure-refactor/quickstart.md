# Quickstart — Phase 002 ValidationMeasure (Backend)

Reproduces the full happy path in under 5 minutes against a local dev environment.

## Prerequisites

- The Spring Boot app is running on port 8089.
- Keycloak is up on `http://localhost:8180`, realm `sageline`.
- A user with role `TECH_VAL` exists; you have a JWT in `$TOKEN`.
- Phase 001's catalog migrations have run (Flyway has applied `V1.0`…`V1.3`); the Phase 002 migrations `V2.0`…`V2.2` are applied automatically by Flyway at startup.
- A ticket exists in status `EN_COURS` on a zone whose poste type has a non-empty catalog. Replace `42` below with that ticket's id.

```powershell
$TOKEN  = "<your JWT>"
$BASE   = "http://localhost:8089"
$TICKET = 42
$H      = @{ Authorization = "Bearer $TOKEN"; "Content-Type" = "application/json" }
```

## 1. Seed the ticket with NOT_EXECUTED placeholders from the catalog

```powershell
Invoke-RestMethod -Method Post -Uri "$BASE/api/validations/$TICKET/measures/from-template" -Headers $H
```

Expected: HTTP 200 with a JSON array. Each item has `status="NOT_EXECUTED"`, `measuredValue=null`, `deviationPct=null`, and the catalog template's bounds/unit/context copied onto it.

Re-run the same call: the response array is empty (idempotency, SC-005).

## 2. List the seeded measures

```powershell
Invoke-RestMethod -Method Get -Uri "$BASE/api/validations/$TICKET/measures" -Headers $H | Format-Table id, measureCode, status, measuredValue
```

## 3. Record one measure value

Pick a `measureId` from step 2 (call it `$MID`).

```powershell
$body = @{ measuredValue = 15.5 } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$BASE/api/validations/$TICKET/measures/$MID" -Headers $H -Body $body
```

If the bounds on that template are `[13.5, 16.5]`, the response shows `status="OK"`, `deviationPct ≈ 33.3` (canonical SC-001 fixture).

Try an out-of-range value:

```powershell
$body = @{ measuredValue = 20.0 } | ConvertTo-Json
Invoke-RestMethod -Method Put -Uri "$BASE/api/validations/$TICKET/measures/$MID" -Headers $H -Body $body
```

Response: `status="OUT_OF_RANGE"`, `deviationPct ≈ 433` (second canonical fixture).

Reset to NOT_EXECUTED by sending `measuredValue=null`:

```powershell
$body = '{"measuredValue":null}'
Invoke-RestMethod -Method Put -Uri "$BASE/api/validations/$TICKET/measures/$MID" -Headers $H -Body $body
```

Response: `status="NOT_EXECUTED"`, `deviationPct=null` (third canonical fixture).

## 4. Record several values in one transactional batch

```powershell
$body = @{
  measures = @(
    @{ templateId = 101; measuredValue = 15.5 },
    @{ templateId = 102; measuredValue = 14.8 },
    @{ templateId = 103; measuredValue = 16.0 }
  )
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri "$BASE/api/validations/$TICKET/measures/batch" -Headers $H -Body $body
```

Expected: HTTP 201, three response items, each with status/deviation set.

Force a rejection by including an unknown template id:

```powershell
$body = @{
  measures = @(
    @{ templateId = 101; measuredValue = 15.5 },
    @{ templateId = 999999; measuredValue = 1.0 }
  )
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Method Post -Uri "$BASE/api/validations/$TICKET/measures/batch" -Headers $H -Body $body
} catch {
  $_.Exception.Response.StatusCode  # → 422
  $_.ErrorDetails.Message            # → BatchMeasureErrorResponse JSON with index=1
}
```

The first entry is *not* persisted — verify with step 2's GET.

## 5. Record an ad-hoc measure outside the catalog

```powershell
$body = @{
  measureCode = "SCRATCH_TEST"
  measureLabel = "Scratch test (ad-hoc)"
  category = "OTHER"
  unit = "V"
  lowerBound = 10.0
  upperBound = 14.0
  measuredValue = 12.0
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BASE/api/validations/$TICKET/measures" -Headers $H -Body $body
```

Response: `catalogTemplateId=null`, `status="OK"`, `deviationPct ≈ 50` (12 is one quarter of the half-range away from the center 12.0… correction: center=12, halfRange=2, deviation=|12-12|/2*100=0). Adjust the measured value to verify other bounds.

## 6. Confirm the legacy endpoint still works (and is deprecated)

```powershell
$resp = Invoke-WebRequest -Method Get -Uri "$BASE/api/validation-results/validation/$TICKET" -Headers $H
$resp.StatusCode                                # → 200
$resp.Headers["Deprecation"]                    # → 'true'
```

## 7. Verify role gating

Repeat step 3 with a JWT carrying only the `CHEF_SECTEUR` role: the response is HTTP 403.

## 8. Verify ticket-status gating

Pick a ticket that is not in `EN_COURS` (e.g., `EN_REVUE`). Attempt step 3 against any of its measures: HTTP 422 with the message identifying the ticket's current status.

## Cleanup

```powershell
# Delete the ad-hoc measure created in step 5
Invoke-RestMethod -Method Delete -Uri "$BASE/api/validations/$TICKET/measures/$adhocId" -Headers $H
```

Steps 1–6 demonstrate every functional requirement in the spec; steps 7–8 demonstrate the two cross-cutting gates (role and status).
