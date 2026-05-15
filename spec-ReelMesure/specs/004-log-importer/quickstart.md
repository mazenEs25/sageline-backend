# Quickstart — Sagemcom Log Importer (Phase 004)

**Goal**: Reproduce the full preview → confirm → re-import → snippet flow in under 5 minutes against a local SageLine instance, using a real Sagemcom log fixture.

This recipe assumes Phases 001/002/003 are in place. All commands are `curl`-against-`localhost`; replace the JWT placeholder with a token from your local Keycloak.

---

## 0. Prerequisites (one-time)

1. **Backend running.** `./mvnw spring-boot:run` from the repo root. Flyway will apply `V4.0__imported_log_file.sql` and `V4.1__measure_code_alias.sql` automatically on first boot.
2. **Keycloak token.** Grab a JWT for a user holding `TECH_VAL` or `ADMIN_IT`. The rest of the recipe assumes the variable `$TOKEN`.
   ```bash
   TOKEN="eyJhbGciOi..."
   ```
3. **A ticket on a known station.** Create or pick a ticket on a `WIFI_CONDUIT` zone (matches the BWC fixture). The rest of the recipe assumes `$TICKET_ID`.
   ```bash
   TICKET_ID=42
   ```
4. **Fixture file on disk.** The supervisor fixture for this recipe is already in the test tree:
   ```
   src/test/resources/fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log
   ```
   Copy it next to your terminal for convenience, e.g.:
   ```bash
   cp src/test/resources/fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log /tmp/sample.log
   ```

---

## 1. Preview the import (no DB writes)

```bash
curl -s -X POST "http://localhost:8089/api/validations/$TICKET_ID/preview-log" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/sample.log" \
  | jq
```

**Expected output (abridged):**

```json
{
  "detectedFormat": "BWC",
  "totalParsed": 18,
  "matched": [
    {
      "measureCode": "POWER_RMS_AVG_VSA1_ANT1_5250",
      "sourceCode": "POWER_RMS_AVG_VSA1",
      "measuredValue": 15.52,
      "unit": "dBm",
      "lowerBound": 13.5,
      "upperBound": 16.5,
      "computedStatus": "OK",
      "sourceDeclaredStatus": "OK_FROM_LOG",
      "templateId": 142,
      "willPersist": true
    }
    // ... ≥ 15 more
  ],
  "unmatched": [],
  "wouldOverwrite": [],
  "warnings": [],
  "ticketId": 42,
  "dryRun": true
}
```

**What to verify:**

- `detectedFormat: "BWC"` (the header sniff worked).
- `matched.length >= 16` (SC-002).
- `dryRun: true` and no `ImportedLogFile` row exists yet (check the DB or call step 4 — it returns no rows).

---

## 2. Commit the import

```bash
curl -s -X POST "http://localhost:8089/api/validations/$TICKET_ID/import-log" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/sample.log" \
  | jq
```

**Expected output:** identical body to step 1, except `dryRun: false`.

**Side effects:**

- 16+ `ValidationMeasure` rows on `$TICKET_ID` move from `NOT_EXECUTED` to `OK`.
- One `ImportedLogFile` row inserted; the file is on disk at `storage/logs/$TICKET_ID/bwc-gateway-safran-wifi5g.log`.
- A `WorkflowReadinessSnapshot` is published on `/topic/validation.$TICKET_ID.readiness` after `AFTER_COMMIT`.
- `GET /api/validations/$TICKET_ID/readiness` now returns `canTransition: true` (assuming all mandatory measures of the WIFI_CONDUIT catalog are covered by this fixture; otherwise it shows the residual gap).

---

## 3. Re-import: see the `wouldOverwrite` flow

Try a second import without the flag — every row is now in `OK`, so the importer protects them:

```bash
curl -s -X POST "http://localhost:8089/api/validations/$TICKET_ID/import-log" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/sample.log" \
  | jq '.wouldOverwrite | length'
```

**Expected:** `16` (or whatever the matched count was in step 2). The previously imported rows show up under `wouldOverwrite[]` with `willPersist: false` on the corresponding `matched[]` entries.

Now opt in:

```bash
curl -s -X POST "http://localhost:8089/api/validations/$TICKET_ID/import-log" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/sample.log" \
  -F 'options={"overwriteExisting":true};type=application/json' \
  | jq '.matched[0].willPersist'
```

**Expected:** `true`. All rows are now overwritten; the second `ImportedLogFile` row is the latest source for them.

---

## 4. Retrieve a source snippet

Pick any imported measure id (substitute `$MEASURE_ID`):

```bash
curl -s "http://localhost:8089/api/validations/$TICKET_ID/measures/$MEASURE_ID/source-snippet" \
  -H "Authorization: Bearer $TOKEN" \
  | jq
```

**Expected output:**

```json
{
  "measureId": 1234,
  "originalFilename": "bwc-gateway-safran-wifi5g.log",
  "detectedFormat": "BWC",
  "snippet": "Mesure <POWER_RMS_AVG_VSA1_ANT1_5250> : ANT1@5250 - Status 0\n                   13.5 dBm < ... < 16.5 dBm\n                   15.52 dBm\n",
  "available": true,
  "startLine": 422,
  "endLine": 425
}
```

---

## 5. Disaster-recovery fallback (FR-009)

Delete the on-disk log to simulate a missing file:

```bash
rm storage/logs/$TICKET_ID/bwc-gateway-safran-wifi5g.log
```

Re-call the snippet endpoint from step 4. **Expected:**

```json
{
  "measureId": 1234,
  "originalFilename": "bwc-gateway-safran-wifi5g.log",
  "detectedFormat": "BWC",
  "snippet": null,
  "available": false,
  "startLine": null,
  "endLine": null
}
```

The measure rows themselves stay intact.

---

## 6. Negative cases (smoke)

| Scenario | curl shape | Expected HTTP |
|---|---|---|
| Upload > 2 MB | `dd if=/dev/zero of=/tmp/big.log bs=1M count=3` then upload | 413 with `code: LOG_TOO_LARGE` |
| Unsupported header | upload an arbitrary `.txt` file | 422 with `code: UNSUPPORTED_LOG_FORMAT` |
| Wrong role | call with a JWT lacking `TECH_VAL` / `TECH_PREP` / `ADMIN_IT` | 403 |
| Ticket not in EN_COURS | run step 2 on a `PLANIFIE` ticket | 409 with `code: MEASURE_NOT_EDITABLE` (Phase 002 guard) |
| Concurrent import | fire two `import-log` calls in parallel | one 200, one 409 with `code: IMPORT_IN_PROGRESS` |

---

**Total elapsed for a clean run on a developer laptop:** ~90 seconds. The 30-second SC-001 demo target is the *UI* flow, which is out of scope for this phase — the backend numbers above are well within that envelope.
