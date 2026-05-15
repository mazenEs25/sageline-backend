# Feature Specification: Sagemcom Log Importer

**Feature Branch**: `004-sagemcom-log-importer`
**Created**: 2026-05-14
**Status**: Draft
**Input**: User description: "Phase 004 — Sagemcom Log Importer from plan.md"

## Clarifications

### Session 2026-05-14

- Q: When the detected log format does not match the ticket zone's `PosteType`, should the importer hard-block or soft-block the operation? → A: Soft-block — preview surfaces a clear "format mismatch" warning, but the user may still confirm the import; aliased matches persist, non-aliased entries land in `unmatched`.
- Q: On a re-import, what is the overwrite policy for measures already filled on the ticket? → A: By default, overwrite only rows in `NOT_EXECUTED`. Rows already in `OK`/`OUT_OF_RANGE` appear in a dedicated "would-overwrite" preview section and only persist if the user explicitly opts in via a toggle on the commit request. Manual-vs-imported origin is not used as a gate; the toggle protects every already-measured row.
- Q: Should the importer record the log-declared status (Sagemcom 0/1/2) separately from the recomputed status, given that the catalog bounds are authoritative? → A: Yes — store the log-declared status as a separate non-authoritative field on every imported measure, and emit a per-measure warning in the import report whenever it diverges from the recomputed status (signals catalog-vs-station bound drift).
- Q: What is the maximum accepted upload size for a Sagemcom log file? → A: 2 MB. Files exceeding this are rejected before parsing with a clear size-limit error.
- Q: How long is the original imported log file retained on the server? → A: Indefinitely, bound to the parent ticket's lifetime. The log is deleted only when (and if) its ticket is deleted. There is no time-based purge. The FR-009 "source no longer available" fallback covers only disaster-recovery cases (file missing on disk despite the ticket still existing), not a scheduled purge.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Drag-drop a real Sagemcom log to auto-fill a ticket (Priority: P1)

A technician opens an in-progress validation ticket whose measures are mostly `NOT_EXECUTED`. Instead of typing each value, the technician drags the Sagemcom production log file produced by the test station into the ticket. The system parses the log, shows a preview of what will be created (matched measures, unmatched codes, warnings), the technician confirms, and the ticket's measure panel populates with real values and statuses. The workflow readiness bar jumps from "X/N filled" to "N/N filled", and the Submit-for-Review button unlocks live.

**Why this priority**: This is the headline capability of the phase and the centerpiece of the PFE defense demo. Without it, the phase delivers no user-visible value; with it, a jury member can reproduce an end-to-end flow in under 30 seconds from a real supervisor-provided log.

**Independent Test**: Given a ticket created on a `WIFI_CONDUIT` zone with all measures in `NOT_EXECUTED`, a technician drags the supervisor's `bwc-gateway-safran-wifi5g.log` into the Import dialog, confirms the preview, and the measure panel + readiness bar update without a manual page refresh.

**Acceptance Scenarios**:

1. **Given** a ticket in `EN_COURS` on a `WIFI_CONDUIT` zone with 16 mandatory measures in `NOT_EXECUTED`, **When** a `TECH_VAL` user drops the real BWC WiFi gateway log and confirms the preview, **Then** at least 16 measures are created with values matching the log, their status is computed against the catalog bounds, the readiness bar reaches 16/16, and Submit-for-Review becomes enabled.
2. **Given** the same ticket, **When** the user drops the BNFT decoder log (wrong station for this ticket), **Then** the preview shows the detected format and lists every parsed measure under "Unmatched" with a clear reason, and confirming the import creates no measures (or only the small subset that aliases to the catalog, if any).
3. **Given** an unsupported file (corrupted log, plain text without a final-measure block, or an unsupported station header), **When** the user drops it, **Then** the system returns a user-facing error explaining the file could not be parsed, persists no measures, and does not change the ticket state.

---

### User Story 2 - Preview before commit (Priority: P1)

Before any measure is persisted, the technician sees exactly what the importer found: detected log format, count of matched/unmatched/warning items, every measure to be created with code, value, unit, and computed status. Preview and commit produce identical outputs (preview is just commit without persistence).

**Why this priority**: Imported measures change the ticket's verdict path and unlock workflow transitions; an unreviewable bulk operation is unacceptable in an industrial context. Preview is the safety net.

**Independent Test**: Calling preview on a fixture log returns a structured report; calling import on the same fixture produces the same matched/unmatched lists and persists measures matching the preview.

**Acceptance Scenarios**:

1. **Given** any of the three supervisor fixture logs, **When** the technician requests a preview, **Then** the system returns the detected format, the matched measures with their values and computed statuses, the unmatched measure codes with the reason, and any warnings — without creating any measure.
2. **Given** a preview report has been displayed, **When** the technician cancels the dialog, **Then** the ticket and its measures are unchanged.
3. **Given** a preview report has been displayed, **When** the technician confirms the import, **Then** the persisted measures match the preview's `matched` list one-to-one (same codes, values, statuses), and the unmatched list does not produce any persisted row.

---

### User Story 3 - Source-file traceability per measure (Priority: P2)

Every imported measure carries a link back to the original log file it came from. From the measure panel, a user can see which measures were imported (versus manually entered), open the original filename, and view the exact log snippet that produced the value.

**Why this priority**: Required by Constitution principle 5 (traceability from log to verdict) and Sagemcom audit practice; supports the override/justification flow downstream in Phase 005. Lower priority than the import itself because the measures are usable without it, but the phase is incomplete without auditability.

**Independent Test**: After importing a fixture log, every newly created measure has a non-null source-file reference; clicking the source icon in the UI shows the matching snippet from the stored log.

**Acceptance Scenarios**:

1. **Given** a ticket where 16 measures were imported from `bwc-gateway-safran-wifi5g.log`, **When** any of these measures is inspected, **Then** the system reports the original log filename and can return the snippet of the log that produced its value.
2. **Given** the same ticket also has a manually entered measure, **When** that measure is inspected, **Then** no source-file reference is reported (manual entry is distinguishable from import).
3. **Given** an imported log has been deleted from disk for any reason, **When** a user requests the source snippet, **Then** the system returns a graceful "source no longer available" response rather than an internal error, and the measure itself remains readable.

---

### User Story 4 - Unmatched-code triage by catalog admin (Priority: P3)

When the parser finds measure codes that have no matching template in the ticket zone's `PosteMeasureCatalog`, an `ADMIN_IT` or `CHEF_SECTEUR` user can act on each unmatched code from the preview dialog: dismiss it, or add it to the catalog so that future imports of the same station produce a match.

**Why this priority**: Adds long-term value (the catalog improves with each new product/station encountered) but is not required for the demo flow; out-of-the-box the three supervisor logs match the seeded catalogs.

**Independent Test**: Run a preview against a log containing a code absent from the catalog; an admin uses the inline "Add to catalog" action; re-running the preview shows the same code now under "Matched".

**Acceptance Scenarios**:

1. **Given** an unmatched code in a preview report, **When** an `ADMIN_IT` user adds it to the catalog from the preview dialog with default bounds, **Then** a new template appears in the catalog and a fresh preview of the same log returns the code under "Matched".
2. **Given** an unmatched code, **When** a user without the admin/chef role views the preview, **Then** no "Add to catalog" action is offered to them.

---

### Edge Cases

- **Multiple imports on the same ticket**: a second import on the same ticket re-uses already-instantiated measure rows (update in place) rather than duplicating. The latest source-file reference wins; no value is silently overwritten without the second import surfacing a "will overwrite N existing values" notice in the preview.
- **Empty ticket (no measure rows yet)**: the import creates measure rows from the catalog template on the fly — the user does not need to "instantiate template measures" first.
- **Log truncated mid-final-block**: parser surfaces a warning, persists only fully parsed entries, and never persists a half-parsed measure.
- **Log with a final-block measure missing the value line** (Status 2 / NOT_EXECUTED in source): imported as `NOT_EXECUTED`, contributing nothing to readiness — the readiness rule still blocks transitions.
- **Wrong-station log on a typed ticket**: header sniffing detects the mismatch; the preview clearly states the detected format and warns the format does not match the ticket's zone `PosteType`. The user can still confirm the import (any aliased matches will go through), or cancel.
- **Concurrent imports**: only one import per ticket at a time. A second concurrent request returns a "import already in progress for this ticket" error rather than racing rows.
- **Very large files**: files above a reasonable size cap (see Assumptions) are rejected before parsing with a clear size-limit error.
- **Workflow state**: imports are only accepted while the ticket is in `EN_COURS` (the only state where measures are editable per Phase 002 `MeasureEditabilityGuard`). Any other state returns a "ticket is not editable" response and persists nothing.
- **Role**: only `TECH_VAL`, `TECH_PREP`, or `ADMIN_IT` can preview or commit an import. Other roles get 403.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept a Sagemcom production log file (`.log` or `.txt`) uploaded against a specific validation ticket for either preview or import.
- **FR-002**: System MUST auto-detect the log format among the three known Sagemcom station families (BNFT, BWC, BTF) using header sniffing, and MUST surface the detected format in the response.
- **FR-003**: System MUST parse the final-measure block of the log and extract, for each entry: measure code, label, source status (0 = OK, 1 = OUT_OF_RANGE, 2 = NOT_EXECUTED), lower bound, upper bound, unit, and measured value when present.
- **FR-004**: System MUST match each parsed measure code to a template in the ticket zone's `PosteMeasureCatalog` (primary lookup) or via a configurable alias table (secondary lookup), and MUST classify every parsed entry as either matched or unmatched with a reason.
- **FR-005**: System MUST provide a non-mutating preview operation that returns the import report (detected format, matched, unmatched, warnings) without persisting any measure.
- **FR-006**: System MUST provide a commit operation that persists matched measures on the ticket and produces a result equivalent to the preview report.
- **FR-007**: System MUST compute each persisted measure's authoritative status and deviation from the catalog bounds using the existing `MeasureDeviationCalculator` rules (Phase 002), regardless of the source status declared in the log. The system MUST additionally persist the log-declared status as a separate non-authoritative field on the imported measure, and MUST emit a per-measure warning in the import report for every measure where the log-declared status disagrees with the recomputed status.
- **FR-008**: System MUST persist, for every imported measure, a reference to the original uploaded file so that the source can be inspected later. The uploaded file MUST be retained for the lifetime of the parent ticket (no time-based purge); it is deleted only when its parent ticket is deleted.
- **FR-009**: System MUST expose the original log snippet that produced a given imported measure for audit purposes, and MUST return a graceful response when the source is no longer available.
- **FR-010**: System MUST reject imports when the ticket is not in `EN_COURS` (consistent with the Phase 002 editability guard) and MUST persist no measure in that case.
- **FR-011**: System MUST restrict preview and import operations to the roles `TECH_VAL`, `TECH_PREP`, and `ADMIN_IT`.
- **FR-012**: System MUST reject corrupted logs, logs missing a final-measure block, and logs from unsupported stations with a clear error and persist nothing.
- **FR-013**: System MUST enforce a single-import-at-a-time invariant per ticket and surface a clear error on concurrent attempts.
- **FR-014**: System MUST enforce a maximum upload size of 2 MB per log file and reject larger uploads before parsing with a clear size-limit error.
- **FR-015**: System MUST treat a repeat import on a ticket as an upsert by measure code. By default, only rows currently in `NOT_EXECUTED` are persisted; rows already in `OK` or `OUT_OF_RANGE` MUST be surfaced in the preview under a dedicated `wouldOverwrite` section and MUST NOT be persisted unless the caller sets an explicit `overwriteExisting=true` opt-in on the commit request. The preview MUST always disclose the count and codes of would-be overwrites regardless of the opt-in value.
- **FR-016**: System MUST trigger workflow-readiness recomputation (Phase 003) after a successful import so that downstream consumers (UI, WebSocket subscribers) see the updated state.
- **FR-017**: System MUST commit imported measures atomically — either every matched measure is persisted with its source-file link or none is (a partial-success state must not be observable on the ticket).
- **FR-018**: System MUST integrate three real supervisor-provided log files as test fixtures and prove parsing correctness against them.

### Key Entities *(include if feature involves data)*

- **ImportedLogFile**: The persisted original log uploaded against a ticket. Carries the ticket reference, original filename, detected format, upload timestamp, the uploading user, and a content reference sufficient to retrieve the original bytes (or at minimum the snippet for any measure it produced). One ticket may have several over time.
- **LogImportReport**: The transient result of a preview or commit operation. Carries detected format, totals, the list of matched parsed measures (code, value, status, target template) and unmatched parsed measures (code, reason), and warnings. Preview-mode reports are not persisted; commit-mode reports may be stored as part of the imported-log-file record for audit.
- **MeasureCodeAlias**: A configurable mapping from a log-level measure code to a catalog measure code, scoped to a `PosteType`. Allows catalog stability while accommodating naming variations across log generations. Out of scope to provide a UI editor in this phase — seeded entries are enough for the supervisor logs.
- **ValidationMeasure (existing, extended)**: Gains a non-null link to the source `ImportedLogFile` when created via import; remains link-less when created manually. Imported rows additionally carry a non-authoritative `sourceDeclaredStatus` field reflecting the Sagemcom Status 0/1/2 as it appeared in the log.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A jury member can drag-drop the supervisor's BWC WiFi gateway log into a freshly created `WIFI_CONDUIT` ticket, review the preview, confirm, and see the measure panel and readiness bar populate end-to-end in under 30 seconds.
- **SC-002**: All three supervisor-provided real log fixtures parse to at least the documented measure counts (BNFT ≥ 6, BWC ≥ 16, BTF ≥ 14 power/voice measures), with at least 3 measure values per fixture exactly matching the source log.
- **SC-003**: Preview and commit produce the same matched/unmatched lists for any given input (zero divergence across the three fixtures).
- **SC-004**: 100% of measures created through the importer carry a working source-file reference, verifiable by retrieving the original snippet.
- **SC-005**: For an empty ticket on a known station, importing the corresponding station log brings workflow readiness to 100% (`canTransition` true) in a single user action, without any manual measure entry.
- **SC-006**: A re-import on a ticket never produces duplicate measure rows for the same `measureCode`. Without the `overwriteExisting` opt-in, no row already in `OK`/`OUT_OF_RANGE` is altered; with the opt-in, every `wouldOverwrite` row from the preview is replaced one-to-one.
- **SC-007**: Corrupted, truncated, or wrong-station logs surface a clear actionable message and create zero measures, in 100% of negative-fixture tests.
- **SC-008**: Concurrent import attempts on the same ticket never produce inconsistent state (no half-imported rows, no duplicated rows) in load-test conditions.

## Assumptions

- The three supervisor-provided log files (BNFT decoder M393, BWC gateway Safran WiFi V4.0, BTF gateway WiFi7 FB107) are the canonical fixtures and live (or will live) under `src/test/resources/fixtures/sagemcom-logs/` in the backend tree. They define both the parser specification and the success-criteria measure counts.
- Phase 001 (`PosteMeasureCatalog`) is in place and seeded for at least `TEST_FONCTIONNEL`, `WIFI_CONDUIT`, and one BTF-equivalent poste so that the three fixtures produce a "matched" majority out of the box. Where the plan references `ACC` for the BTF log, this spec defers the exact `PosteType` choice to plan stage as long as ≥14 measures of the BTF fixture match.
- Phase 002 (`ValidationMeasure`, `MeasureDeviationCalculator`, `MeasureEditabilityGuard`) is in place. The importer reuses the calculator and obeys the guard rather than re-implementing classification or editability logic.
- Phase 003 (`WorkflowReadiness`, WebSocket readiness topic) is in place. After an import, the importer triggers a readiness recomputation through the existing service rather than re-implementing the rule.
- Uploaded logs are stored at the application level (filesystem under a server-controlled directory, or a blob column) — the exact persistence target is a plan-stage decision. Either is acceptable as long as the source snippet can still be returned per FR-009 and a graceful fallback exists when storage is unavailable.
- Maximum log size cap is set at 2 MB per file (see FR-014); the supervisor fixtures are well under this limit.
- ZIP-bundled log uploads are out of scope for this phase (mentioned in `Plan.md` as deferred). Only plain `.log` / `.txt` are accepted.
- The frontend ticket-detail page already hosts the `MeasurePanel` (Phase 002) and the `WorkflowReadinessBar` (Phase 003). The Import dialog plugs into the existing page; no second ticket page is introduced.
- Alias-table maintenance has no admin UI in this phase — entries are managed via configuration or migration. The "Add to catalog" inline action in the preview dialog reuses the existing Phase 001 catalog endpoints; it does not introduce a new alias-management surface.
- Out of scope: reverse export (measures → log), real-time log tail / file watcher, multi-file batch upload, ZIP support, and any AI-assisted disambiguation of unmatched codes.
