# Phase 0 Research: Sagemcom Log Importer

**Feature**: 004-log-importer
**Date**: 2026-05-14
**Inputs**: `spec.md` (with Clarifications session 2026-05-14, 5 Qs), `Plan.md` §9, three real fixture logs under `src/test/resources/fixtures/sagemcom-logs/`.

All Technical-Context items in `plan.md` are resolved (no `NEEDS CLARIFICATION` remaining). The research below records *why* each choice was made and what was rejected, so reviewers can audit the design without rederiving it from the code.

---

## R-001 — Parser architecture: strategy + sniffer

**Decision.** A `HeaderSniffer` reads the first ≤ 4 KB of the upload, scans for known header signatures (`EZR-AVS*` → BNFT, `EZR-BBS27*` + `BWC` → BWC, `EZR-BBS22*` + `BTF` → BTF), and selects exactly one `LogFormatStrategy`. The strategy parses the full content and returns a `ParsedLog(format, parsedMeasures, parserNotes)`. The orchestrator (`LogImportPipeline`) is parser-agnostic — it only sees `ParsedLog`.

**Rationale.** Three known formats today; the supervisor will hand us more stations over the year. The strategy split lets us add a fourth format (e.g., a new BBS variant) by registering one more `@Component` and one more header signature — the orchestrator, the matcher, the persistence path, and every test that doesn't touch parsing stay unchanged. The sniffer is a tiny, fully testable unit; isolating it makes "wrong-station" diagnostics (Clarify Q1) trivial — the importer always knows the format before deciding what warnings to attach.

**Alternatives considered.**

- **Single monolithic regex over all three formats.** Rejected: the three formats already differ in their *secondary* scans (BWC has the inline `POWER_RMS_AVG_VSA1` block; BTF has the FXS sub-section), and a monolithic regex would either need conditional branches (just as complex as strategies, but harder to unit-test) or backtracking (slow, brittle).
- **Antlr / a real parser generator.** Rejected: overkill — the log format is a flat key/value/range structure inside a final block, not a recursive grammar. `java.util.regex` plus line-iteration handles it in ~100 lines per strategy.
- **`@ConditionalOnExpression` on each strategy.** Rejected: strategies must coexist at runtime (the user may upload any of the three to any ticket), so `@Component`s wired into a `List<LogFormatStrategy>` is the correct pattern.

---

## R-002 — Wrong-station handling (Clarify Q1)

**Decision.** Soft-block. The pipeline always runs to completion; when the detected format does not correspond to the ticket zone's `PosteType`, the report carries a `WRONG_STATION_FORMAT` warning entry (with the detected format, the ticket's expected `PosteType`, and a short human-readable reason). Aliased matches still persist; non-aliased entries land in `unmatched` as usual.

**Rationale.** Hard-blocking would forbid legitimate cross-station alias entries. The catalog already supports `MeasureCodeAlias` (R-008), and the spec explicitly allows admins to seed aliases for codes that travel across stations. Forbidding the upload at the controller would make those aliases unusable.

**Alternatives considered.**

- **Hard-block (HTTP 422 on mismatch).** Rejected per Clarify Q1. The audit story still holds because the warning is sticky on both preview and commit reports, and the operator sees the mismatch before confirming.
- **Hard-block at commit only, allow preview always.** Rejected: would create two distinct behavioral modes (preview behavior ≠ commit behavior) and would violate SC-003 ("preview and commit produce identical lists for any given input").

---

## R-003 — Overwrite policy on re-import (Clarify Q2)

**Decision.** Upsert by `measureCode`. Default behavior: only rows currently in `NOT_EXECUTED` are replaced; rows in `OK`/`OUT_OF_RANGE` are reported under `wouldOverwrite[]` with both current and proposed values, and persist *only* when the caller passes `overwriteExisting=true` as a multipart form field. Preview always discloses `wouldOverwrite[]` regardless of the flag.

**Rationale.** Industrial audit practice: silently destroying a measured value is unacceptable, even when the new value also passes the tolerance check. The toggle gives the operator one explicit point of opt-in, and the preview makes the consequence visible before the click. Using `status` rather than `sourceLogFile` (manual-vs-imported) as the gate is intentional — a manually entered `OK` value is just as worth protecting as an imported one, and gating on origin would create a privilege gap that has no industrial justification.

**Alternatives considered.**

- **Overwrite everything matched, surface the count.** Rejected: SC-006 explicitly requires no value already in `OK`/`OUT_OF_RANGE` is silently replaced.
- **Gate by origin (overwrite only previously imported rows, never manual ones).** Rejected: creates an asymmetry without industrial backing, and the data-model column `sourceLogFile` is already used for traceability rather than as a permission.
- **Per-row toggle in the preview UI.** Rejected as scope-creep for this phase. A single global flag is enough to unblock SC-006; per-row granularity can be added later without a contract break.

---

## R-004 — Source-status divergence (Clarify Q3)

**Decision.** A new non-authoritative column `validation_measures.source_declared_status` (enum `SourceDeclaredStatus { OK_FROM_LOG, OUT_OF_RANGE_FROM_LOG, NOT_EXECUTED_FROM_LOG }`) records the Sagemcom Status 0/1/2 from the log. The authoritative `status` is still computed by `MeasureDeviationCalculator` (Constitution III). A `SourceStatusReconciler` compares the two; on disagreement it emits a per-measure `STATUS_DIVERGENCE` warning in the import report.

**Rationale.** Catalog bounds drift over the year as Sagemcom re-tunes its stations. A divergence signal is the cheapest possible drift-detection mechanism — no AI, no analytics — and the data is free (it is already in every log we parse). Storing it as a separate column keeps Constitution III intact: `MeasureStatus` remains the single workflow-authoritative state.

**Alternatives considered.**

- **Do not record at all.** Rejected per Clarify Q3 — loses an audit signal that costs near-zero to keep.
- **Store only when divergent (store-on-divergence).** Rejected: complicates the persistence path (conditional writes), prevents simple `WHERE source_declared_status = ...` analytics later, and saves negligible disk.
- **Coerce into `MeasureStatus`.** Rejected — Constitution III: `NOT_EXECUTED` is a first-class workflow signal and must not be polluted by a non-authoritative source. The separate column keeps the contract clean.

---

## R-005 — Upload size cap (Clarify Q4)

**Decision.** 2 MB. Enforced at two layers:

1. **Spring multipart layer.** `spring.servlet.multipart.max-file-size=2MB` + `max-request-size=3MB` (small headroom for the form). Oversized uploads fail early with Spring's `MaxUploadSizeExceededException`, mapped by `GlobalExceptionHandler` to HTTP 413 with a clear message.
2. **Defensive guard.** `LogImportProperties.maxFileSize` re-checks the byte count inside the controller before invoking the pipeline. This makes the cap binding even if someone later changes `application.properties` without thinking — the guard is the load-bearing rule, the multipart config is the fast path.

**Rationale.** The supervisor fixtures are 50–300 KB each. 2 MB is ~7× the largest known file, large enough for verbose station variants we have not yet seen, small enough to make a DoS-via-multipart attack uninteresting. The two-layer enforcement is a pattern already used in `MultipartConfig` of similar Spring services and costs nothing.

**Alternatives considered.**

- **5 MB (the original draft).** Rejected for being conservative without justification.
- **No defensive guard (rely on multipart only).** Rejected because misconfiguration drift is a known failure mode and the guard costs three lines.

---

## R-006 — Retention policy (Clarify Q5)

**Decision.** Files live under `storage/logs/{validationId}/{originalName}`. No time-based purge. The `ImportedLogFile` row is FK-linked to `Validation` with `ON DELETE CASCADE`; deleting the parent ticket also deletes the row, and a `@TransactionalEventListener(phase=AFTER_COMMIT)` on the cascade event deletes the on-disk file. The FR-009 "source no longer available" response is reserved for the disaster-recovery case (file missing on disk while the row still exists).

**Rationale.** Industrial audits look back years; tying retention to the ticket lifetime is the only policy that *cannot* destroy evidence prematurely. Disk usage is bounded by ticket count, not by time, and the upper-bound calculation in `plan.md` (~90 GB at 100k tickets × 3 logs × 300 KB) is well within commodity disk capacity.

**Alternatives considered.**

- **Snippet-only retention (purge full log on commit).** Rejected per Clarify Q5 — would lose the audit story.
- **Time-windowed retention (e.g., 1 year).** Rejected: introduces a scheduled job, a configuration knob, and a class of "audit failed because the log was purged on day 366" incidents. Not worth the disk savings.

---

## R-007 — Concurrency: one import at a time per ticket

**Decision.** A PostgreSQL session-level advisory lock keyed on `('log-import', validationId)`. The `ImportLockService` exposes `tryAcquire(validationId): Closeable` that runs `SELECT pg_try_advisory_lock(hashtext('log-import'), validationId)`; the returned `Closeable` releases via `pg_advisory_unlock(...)`. A failure to acquire returns `ImportInProgressException` → HTTP 409 with the `IMPORT_IN_PROGRESS` code.

**Rationale.** The deploy is single-instance today, but advisory locks scale horizontally without any change. The lock is session-scoped (auto-released on connection drop), so a crashed import does not leave the ticket permanently blocked. `pg_try_advisory_lock` is non-blocking — we surface a 409 immediately rather than queue, which matches the spec's "single-import-at-a-time invariant" (FR-013).

**Alternatives considered.**

- **`synchronized` block keyed on a `Long` cache of ticket IDs.** Rejected: correct only on a single JVM; silently broken in a future cluster.
- **Optimistic locking via a row version on `Validation`.** Rejected: pollutes the `Validation` entity with concurrency plumbing for a concern (import) that is unrelated to the rest of its lifecycle.
- **Distributed lock via Redis/Hazelcast.** Rejected: would add a new infrastructure dependency to satisfy a single per-ticket invariant. Postgres advisory locks are free, durable, and already part of the stack.

---

## R-008 — Alias table (`measure_code_alias`)

**Decision.** New table `measure_code_alias(id, poste_type, source_code, catalog_measure_code, active)` with unique `(poste_type, source_code)`. Seeded via `V4.1__measure_code_alias.sql` with the known equivalences from `Plan.md` §9 (`MES_BNFT_PWR0_2G ≡ PWR_2G_ANT0`, etc.). `MeasureMatcher` does a primary lookup against `PosteMeasureCatalog`, then falls back to the alias table.

**Rationale.** Sagemcom occasionally renames codes across log generations (the same physical measure shows up as `MES_BNFT_PWR0_2G` in one BNFT variant and `PWR_2G_ANT0` in another). Holding aliases in a table rather than in code or in `application.properties` keeps the data versionable through Flyway migrations and lets a future admin UI (deferred) edit it without a redeploy. No alias-management endpoints in this phase per spec scope — the table is read-only at runtime today.

**Alternatives considered.**

- **Hardcoded `Map<...>` in a Java class.** Rejected: every alias change becomes a code change + redeploy.
- **`application.yml` map.** Rejected: still requires a restart, and the data is too domain-specific to live in environment config.
- **Extend `PosteMeasureCatalog` with a synonym column.** Rejected: would couple two concerns (catalog templates and naming variants) in one table and make the unique constraint `(posteType, measureCode)` harder to reason about.

---

## R-009 — Atomic commit and disk cleanup

**Decision.** The persist phase looks like this:

1. `LogStorageService.persist(file)` writes the upload to `storage/logs/{validationId}/{originalName}` **before** the `@Transactional` boundary opens. The temp path is captured in a `StoredFile` value object.
2. The pipeline opens a `@Transactional` block: insert `ImportedLogFile` row pointing at the stored path; for each matched / opted-in row in `wouldOverwrite[]`, upsert the `ValidationMeasure` via the existing Phase 002 service (which already runs `MeasureDeviationCalculator`); set `imported_log_file_id` and `source_declared_status` on each.
3. Register two `TransactionSynchronization` callbacks:
   - `AFTER_ROLLBACK` → `LogStorageService.delete(storedFile.path)`. Disk is cleaned up so a rolled-back import leaves no orphan.
   - `AFTER_COMMIT` → `WorkflowReadinessService.publishSnapshot(validationId)` to push a fresh readiness snapshot to the STOMP topic.

**Rationale.** Writing the file *outside* the transaction is the cheapest way to keep an audit copy even if the DB rolls back — useful when investigating a failed import. The `AFTER_ROLLBACK` listener guarantees we don't leave junk on disk in the happy-rollback case (validation failure, advisory-lock contention, etc.). `AFTER_COMMIT` for the STOMP publish is important — publishing before commit would race the readiness subscribers against a transaction that might still roll back.

**Alternatives considered.**

- **File write inside the transaction.** Rejected: a DB rollback would then need a compensating delete anyway, and the file wouldn't be inspectable when investigating the failure.
- **Two-phase: write disk → commit → publish synchronously.** Rejected: synchronous publish inside the transaction creates a fail mode where commit succeeds but publish fails — the synchronization-callback path is well-trodden in Spring (`TransactionSynchronizationManager`) and handles the after-commit failure as a logged warning rather than a transactional fault.

---

## R-010 — Storage layout, snippet retrieval, FR-009 fallback

**Decision.** Constitution V's layout is binding: `storage/logs/{validationId}/{originalName}`. Snippet retrieval is line-based: when `GET /api/validations/{id}/measures/{measureId}/source-snippet` is called, the service:

1. Looks up the `ValidationMeasure` → its `ImportedLogFile` → its `storedPath`.
2. If the file is missing on disk, returns `SourceSnippetDTO { originalFilename, snippet: null, available: false }` with HTTP 200 (graceful, per FR-009).
3. Otherwise, opens the file, locates the line range containing the measure's `measureCode` in the final-measure block, and returns ±`snippetLines` lines around it (default 12).

**Rationale.** Returning HTTP 200 with `available: false` rather than 404 keeps the front-end's flow simple — the paperclip icon greys out instead of throwing an error. The line-based extraction is cheap (logs are < 2 MB and the snippet lookup is `O(file size)`), so no indexing is needed.

**Alternatives considered.**

- **Indexed snippet (precompute offset per measure at import time).** Rejected: premature optimization. Files are small, snippet requests are rare (only on UI hover/click).
- **404 on missing file.** Rejected per FR-009 — the spec explicitly asks for a graceful fallback, and 404 would force every UI consumer to add try/catch handling.

---

## R-011 — Editability and workflow integration

**Decision.** The pipeline calls `MeasureEditabilityGuard.requireEditable(validation)` before any persistence step. The guard's existing rule — "ticket must be in `EN_COURS`" — is reused verbatim. After `AFTER_COMMIT`, the pipeline calls `WorkflowReadinessService.publishSnapshot(validationId)` exactly once; downstream STOMP subscribers receive the post-import readiness via the existing `/topic/validation.{id}.readiness` topic.

**Rationale.** Two existing services own these rules. Duplicating them in the importer would create a class of "guard skipped because importer forgot" bugs and would diverge from the constitution. The publish call is *not* inside the guard's purview — the importer owns the timing of its own snapshot fire.

**Alternatives considered.**

- **Re-implement editability check locally.** Rejected — duplication, drift risk, and violates the "single guard entry point" pattern.
- **Skip the explicit publish call; rely on the per-measure publish hook from Phase 003.** Rejected: the per-measure publish fires for every upsert, which means a 16-measure import would publish 16 snapshots. The explicit single-call form is one snapshot per import — much friendlier to subscribers and indistinguishable in correctness terms.

---

## R-012 — Fixture verification (Constitution VII)

**Decision.** The three supervisor fixtures already live on disk at the canonical path:

- `src/test/resources/fixtures/sagemcom-logs/bnft-decoder-M393.txt`
- `src/test/resources/fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log`
- `src/test/resources/fixtures/sagemcom-logs/btf-gateway-fb107-wifi7.log`

(Verified by `ls` during planning.) Strategy unit tests, lifecycle integration tests, and acceptance tests for Stories 1–3 all consume them. Synthetic content is only used for the four negative cases (corrupted, unsupported-station, missing-final-block, oversized).

**Rationale.** Constitution VII (NON-NEGOTIABLE): real fixtures or no tests. The fixtures define both the parser specification (the regex extracts what they contain) and the SC-002 numeric targets (≥6, ≥16, ≥14 measures respectively).

**Alternatives considered.** None — the principle does not admit any.

---

## Open items deferred to Phase 2 (`/speckit-tasks`)

- Exact `PosteType` chosen for the BTF fixture (spec Assumptions). The decision is local to Phase 001 catalog seeding and does not change any contract here. Will be pinned in `tasks.md` along with the seed migration referenced by `BtfImportLifecycleIT`.
- Per-PR perf measurements against the 800 ms p95 budget. Tracked as one of the acceptance gates in `tasks.md`.
