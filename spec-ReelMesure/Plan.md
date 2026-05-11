# SageLine Full-Stack Refactor — Master Plan

> **Spec-Driven Development plan for transforming SageLine (Spring Boot backend + Angular frontend) from a generic validation tracker into an industrial-grade quality validation platform aligned with real Sagemcom production-line measurements.**

---

## 📑 Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Context & Motivation](#2-context--motivation)
3. [Target Architecture Overview](#3-target-architecture-overview)
4. [Methodology — Spec Kit](#4-methodology--spec-kit)
5. [Project Constitution](#5-project-constitution)
6. [Phase 001 — PosteType Catalog](#6-phase-001--postetype-catalog)
7. [Phase 002 — ValidationMeasure Refactor](#7-phase-002--validationmeasure-refactor)
8. [Phase 003 — Workflow Guard](#8-phase-003--workflow-guard)
9. [Phase 004 — Sagemcom Log Importer](#9-phase-004--sagemcom-log-importer)
10. [Phase 005 — Conformity Engine](#10-phase-005--conformity-engine)
11. [Phase 006 — KPIs by Poste & Measure](#11-phase-006--kpis-by-poste--measure)
12. [Dependency Graph & Sequencing](#12-dependency-graph--sequencing)
13. [Defense Narrative](#13-defense-narrative)

---

## 1. Executive Summary

SageLine, in its current state, models validation tickets with a generic schema: `parameter`, `measuredValue`, `expectedValue`, `conform`. This works for a school project, but it is **disconnected from the reality of Sagemcom's industrial validation process**, where measurements:

- are identified by **normalized codes** (`MES_BNFT_PWR0_2G`, `M_FXS_TRANS_FXS1_1000HZ`, `POWER_RMS_AVG_VSA1`),
- are validated by **tolerance bounds** `[min, max]`, not a single target value,
- have a **3-valued status** (OK / OUT_OF_RANGE / NOT_EXECUTED, corresponding to Sagemcom's Status 0/1/2),
- are produced by **typed test stations** (`PosteType`) — each station has its own catalog of expected measures.

This document defines a **6-phase full-stack refactor** spanning the Spring Boot backend and the Angular 17 frontend, following the GitHub Spec Kit methodology (`/specify → /clarify → /plan → /tasks → /implement`), to align SageLine with this industrial reality. The output is a system that:

| Capability | Before | After |
|---|---|---|
| Measure schema | Generic key/value | Industrial nomenclature, bounded tolerance, status enum |
| Workflow transitions | Free transitions | Guarded by measure-coverage rules per PosteType |
| Data entry | Manual only | Manual **+ Sagemcom log import** (drag-drop real `.log` / `.txt` files) |
| Conformity verdict | Manual only | Auto-computed + human override with traceability |
| KPIs | Per production line | Per PosteType + per measure code, with trends |
| Frontend ticket view | Static parameter list | Live readiness bar, per-measure status badges, drag-drop log import |
| Frontend KPI dashboard | Line-level only | Poste-type breakdown, top-deviant measures, measure trends |
| Defense story | "I built a CRUD" | "I modeled a real industrial domain end-to-end from production logs" |

The refactor is **AI-agnostic** — no AI pillars are touched. Once backend and frontend are solid, AI pillars (semantic memory, forecasting, RAG, agent) plug onto a richer, more credible data substrate and a UI ready to surface their outputs.

**Estimated effort:** ~3–4 weeks total. Each phase has a backend track and a frontend track; the frontend track of phase N typically starts when the backend track of phase N is contract-stable (i.e., DTOs and endpoints frozen).

---

## 2. Context & Motivation

### 2.1 Trigger

The Sagemcom industrial supervisor provided **three real production log files**:

| Log file | Station | Product Under Test | Measures captured |
|---|---|---|---|
| `253994454ASC022...` | TEST_BNFT (Bench Non-Functional Test) | Decoder M393_BT | Wi-Fi 2G/5G power, BT power, IR, LEDs |
| `SN_254050756...ATR_TEST_WIFI_CONDUIT_2...` | ATR_TEST_WIFI_CONDUIT | Gateway Safran WIFI V4.0 | Power RMS, PER, RSSI across 4 antennas × multiple frequencies (5250–5670 MHz) |
| `SN_254087542...L3_SLOT2...` | TEST_BTF (Bench Test Fonctionnel) | Gateway WIFI7 FB107 | FXS voice (current, voltage, ring, 300/1000/3400 Hz transmission), consumption, sensors |

All three logs share a **common final-summary block** structured as:

```
Mesure <CODE>    : Label - Status {0|1|2}
                   <min> <unit> < ... < <max> <unit>
                   <measured> <unit>
```

This structure is the **industrial source of truth** that the backend should mirror.

### 2.2 Gap analysis

| Aspect | Current SageLine | Sagemcom reality | Gap |
|---|---|---|---|
| Measure identifier | free `parameter` string | Codified (`MES_*`, `M_*`, `POWER_*`) | No normalization |
| Tolerance | single `expectedValue` | bounded `[min, max]` | Wrong semantic |
| Unit | implicit | explicit (dBm, mA, V, VRms, dB, W, °C, %, MHz, s) | Missing |
| Status | boolean `conform` | 3-state (OK / OUT_OF_RANGE / NOT_EXECUTED) | Loss of information |
| Context | none | antenna, frequency, modulation scheme | Missing for RF tests |
| Source traceability | none | `.log` file path | Missing |
| Station-aware schema | none | each `PosteType` has its own measure set | Missing |

### 2.3 PosteType — the structural pivot

The `PosteType` enum is the **structural pivot** of the entire validation line:

```java
public enum PosteType {
    // BBS CMS
    ACC, TEST_FONCTIONNEL, WIFI_CONDUIT, WIFI_RY,
    BANC_RX_TX, BANC_SENSI, BANC_TT, BANC_TX,

    // BBS Intégration
    AQ_LIGNE, TELECHARGEMENT, BANC_NFT, BANC_NFT_BOUTON,
    TEST_BOUTON, TEST_VISION, TEST_DOCSIS, TEST_SYNCHRO_GPON,

    // AVS CMS
    BANC_AUDIO_VIDEO, BANC_WIFI_CONDUIT,

    // AVS Intégration
    BPO, FSOS, BANC_ETANCHEITE, BANC_ACOUSTIQUE
}
```

Every refactor decision is anchored on this enum: each `PosteType` defines **what** to measure, **with which bounds**, **in which unit**, and **whether mandatory** for the ticket to move forward.

---

## 3. Target Architecture Overview

```
ProductionLine (BBS_CMS / BBS_INTEG / AVS_CMS / AVS_INTEG)
    │
    └── ValidationZone (physical instance of a station)
            │  zoneCode: "L6_SLOT1"
            │  posteType: WIFI_CONDUIT       ← pivot
            │
            ├──> PosteMeasureCatalog[]       ← reference: WHICH measures are expected
            │       measureCode: "POWER_RMS_AVG_VSA1"
            │       lowerBound: 13.5  upperBound: 16.5  unit: "dBm"
            │       mandatory: true
            │       antenna: "ANT1"  frequencyMhz: 5500
            │
            └──> Validation (Ticket)
                    │ status: EN_COURS
                    │
                    ├──> ValidationMeasure[]   ← actual values entered/imported
                    │       templateId, measuredValue, status, deviationPct
                    │       sourceLogFile (traceability)
                    │
                    ├──> WorkflowReadiness (computed)
                    │       mandatoryTotal: 16
                    │       mandatoryFilled: 14
                    │       canTransition: false
                    │       missingMeasures: [...]
                    │
                    └──> ConformityReport (on closure)
                            verdict: NON_CONFORME
                            blockingMeasures: [...]
                            overrideVerdict?: CONFORME
                            overrideJustification: "Acceptable drift validated by expert"
```

**Core rule:** a ticket cannot transition `EN_COURS → EN_REVUE` unless all `mandatory` measures from its zone's `PosteMeasureCatalog` have a `MeasureStatus ≠ NOT_EXECUTED`.

---

## 4. Methodology — Spec Kit

Each phase is a **GitHub Spec Kit feature** with its own folder under `specs/NNN-phase-name/` and follows the canonical flow:

```
/speckit.specify   →  spec.md          (what & why)
/speckit.clarify   →  clarifications resolved inline
/speckit.plan      →  plan.md
                      research.md
                      data-model.md
                      contracts/       (OpenAPI fragments)
                      quickstart.md
/speckit.tasks     →  tasks.md         (atomic, ordered, parallelizable)
/speckit.implement →  code on a feature branch
```

A phase is **done** when:
- all tasks in `tasks.md` are checked off,
- contract tests pass,
- integration tests pass with real Sagemcom log fixtures (where applicable),
- a quickstart in `quickstart.md` lets a reviewer reproduce the feature in <5 minutes.

---

## 5. Project Constitution

Immutable principles. Every phase respects them; if a phase must deviate, the deviation is documented in `plan.md` under `Constitution Check`.

1. **Industrial fidelity.** All measure nomenclature, tolerance semantics, and workflow rules mirror the conventions observed in real Sagemcom production logs. No invented domain terms.
2. **Bounded tolerance, not target.** Measures are validated by `[lowerBound, upperBound]`. The legacy single `expectedValue` is deprecated.
3. **Three-valued measure status.** Every measure carries `MeasureStatus ∈ {OK, OUT_OF_RANGE, NOT_EXECUTED}` aligned with Sagemcom's Status 0/1/2.
4. **Guarded transitions.** No ticket status transition exists without an explicit, tested business rule encoded in a transition guard.
5. **Traceability from log to verdict.** Every `ValidationMeasure` can be traced back to its source log file (or to the user who entered it manually).
6. **DTO / entity separation.** JPA entities are never exposed in REST. Request and response DTOs in `dtos/request/` and `dtos/response/`. Mappers in `mappers/`. Frontend models in `src/app/models/` mirror response DTOs one-to-one.
7. **Real-log test fixtures.** The three Sagemcom logs provided by the supervisor are committed under `src/test/resources/fixtures/sagemcom-logs/` and used as integration-test inputs for the importer and parser.
8. **Backward compatibility during refactor.** Phase 002 deprecates the old `ValidationResult` API; it must continue to respond (with `Deprecation` HTTP header) for at least one phase before removal. The frontend keeps a thin compatibility shim that reads the new endpoints first and falls back to the legacy ones only if a 404 is returned.
9. **Auditability of overrides.** Whenever a human verdict differs from the computed verdict, the override is persisted with operator identity, timestamp, and free-text justification. The frontend always shows both the computed and the final verdict side by side.
10. **No premature AI integration.** AI pillars are out of scope for this refactor. The data model and the UI must, however, be AI-ready (clear codes, units, contexts; placeholder slots in the ticket detail page for future risk badges) so pillars 1–4 can plug in later without schema or UI rewrite.
11. **Frontend stack consistency.** All new components are NgModule-based (`standalone: false`), declared in `app.module.ts`, use the centralized PrimeNG module, the `lara-dark-blue` theme, the `--sage-*` CSS variables, DM Sans / JetBrains Mono fonts, and the `app` component prefix. No new UI library is introduced.
12. **Role-gated UI.** Every new route declares `data: { roles: [...] }` and is protected by `AuthGuard`. Every destructive or status-changing action is hidden (not just disabled) for users without the required role.

---

## 6. Phase 001 — PosteType Catalog

### Goal
Transform the `PosteType` enum into a **catalog source-of-truth**: for each poste type, a reference list of expected measures (code, label, category, unit, default bounds, mandatory flag, optional antenna/frequency context).

### Why first
Nothing else can be built without this catalog. Workflow guards (Phase 003) need it to know what to check. Log importer (Phase 004) needs it to map parsed measure codes to a target template. KPIs (Phase 006) need it for per-PosteType aggregations.

### Scope
- New enum `MeasureCategory` — `POWER`, `VOLTAGE`, `CURRENT`, `FREQUENCY`, `TIME`, `TEMPERATURE`, `PER`, `RSSI`, `EVM`, `OTHER`.
- New enum `MeasureStatus` — `OK` (0), `OUT_OF_RANGE` (1), `NOT_EXECUTED` (2).
- New entity `PosteMeasureCatalog`:
    - `id`, `posteType` (enum), `measureCode` (unique within posteType), `measureLabel`, `category`, `defaultUnit`, `defaultLowerBound`, `defaultUpperBound`, `mandatory`, `displayOrder`, `antenna?`, `frequencyMhz?`, `modulationScheme?`.
- Repository, service, controller, DTOs (request + response), mapper.
- Endpoints:
    - `GET /api/poste-catalog` — list all
    - `GET /api/poste-catalog/{posteType}` — full catalog of one poste type
    - `GET /api/poste-catalog/{posteType}/measures` — only measures
    - `POST /api/poste-catalog/measures` — add a measure template (ADMIN_IT, CHEF_SECTEUR)
    - `PUT /api/poste-catalog/measures/{id}` — update bounds/mandatory flag
    - `DELETE /api/poste-catalog/measures/{id}` — soft-delete (`active = false`)
- Seed data via Liquibase/Flyway migration for three documented postes (from the three logs):
    - `TEST_FONCTIONNEL`: 6 measures (PWR0_2G, PWR1_2G, PWR0_5G, PWR1_5G, PWR0_BT, Temps_Test)
    - `WIFI_CONDUIT`: 16 measures (POWER_RMS_AVG_VSA1 × 4 antennas × 4 frequencies) + PER + RSSI
    - `ACC`: ~14 measures (FXS current/voltage/ring/transmission, consumption, sensors)
- Validation: a `(posteType, measureCode)` pair is unique (DB constraint + DTO validator).

### Out of scope
- Editing the `PosteType` enum itself (constants stay code-defined).
- UI catalog editor (frontend is a later concern).

### Backend deliverables
- Code on branch `001-poste-type-catalog`
- Liquibase migration `V1.1__poste_catalog.sql` + `V1.2__seed_poste_catalog.sql`
- Contract tests for all 6 endpoints
- Integration test verifying seeded catalog content
- `quickstart.md`: how to add a new measure template via `curl`

### Frontend deliverables
- New service `PosteCatalogService` (`src/app/services/poste-catalog.service.ts`) wrapping the 6 endpoints with typed methods returning `Observable<PosteMeasureCatalog[]>`.
- New models in `src/app/models/`: `poste-measure-catalog.model.ts`, `measure-category.enum.ts`, `measure-status.enum.ts` with companion `*_LABELS`, `*_COLORS`, `*_ICONS` maps (matching existing enum convention).
- New admin page `poste-catalog` under `src/app/pages/Admin/poste-catalog/` (role-gated `ADMIN_IT`, `CHEF_SECTEUR`):
    - List view: filterable PrimeNG table by `PosteType`, columns `measureCode`, `category`, `unit`, `lowerBound`, `upperBound`, `mandatory`, `antenna`, `frequencyMhz`.
    - Detail / edit dialog: create or update a measure template.
    - Bulk-import dialog: paste a JSON array of templates for a given `PosteType` (useful for seeding new postes without touching code).
- New route `admin/poste-catalog` added to `app-routing.module.ts` with `data: { roles: ['ADMIN_IT', 'CHEF_SECTEUR'] }`.
- Sidebar menu entry under the "Administration" group, visible only for the two roles above.
- Shared component `MeasureBadge` (`src/app/shared/components/measure-badge/`) — small chip showing category icon + code + optional antenna/frequency suffix. Reused in Phases 002, 003, 004.
- Karma/Jasmine tests for `PosteCatalogService` HTTP calls.

### Acceptance criteria
- `GET /api/poste-catalog/WIFI_CONDUIT/measures` returns ≥16 measures matching the WIFI_CONDUIT log content.
- Attempting to insert a duplicate `(posteType, measureCode)` returns HTTP 409.
- An EXPERT calling `POST /api/poste-catalog/measures` receives 403.
- The admin page at `/admin/poste-catalog` loads, lists the seeded catalogs grouped by `PosteType`, and lets an `ADMIN_IT` user create a new measure template end-to-end.

---

## 7. Phase 002 — ValidationMeasure Refactor

### Goal
Replace the legacy `ValidationResult` entity with `ValidationMeasure`, aligned with the bounded-tolerance, status-aware industrial schema.

### Why
The current `ValidationResult` schema (`parameter`, `measuredValue`, `expectedValue`, `conform`) cannot represent Sagemcom measures faithfully. This phase delivers the schema that the rest of the system depends on.

### Scope
- New entity `ValidationMeasure`:
    - `id`, `validation` (FK), `catalogTemplate` (FK to `PosteMeasureCatalog`, nullable for ad-hoc measures), `measureCode`, `measureLabel`, `category`, `measuredValue`, `unit`, `lowerBound`, `upperBound`, `status` (enum), `antenna?`, `frequencyMhz?`, `modulationScheme?`, `deviationPct`, `measuredAt`, `enteredBy` (FK user), `sourceLogFile?`.
- `MeasureDeviationCalculator` service implementing:
  ```
  center    = (lowerBound + upperBound) / 2
  halfRange = (upperBound - lowerBound) / 2
  deviation = abs(measured - center) / halfRange * 100
  status    = OK              if  measured ∈ [lowerBound, upperBound]
              OUT_OF_RANGE    otherwise
              NOT_EXECUTED    if  measured is null
  ```
- Repository, service, controller, DTOs, mapper.
- Endpoints:
    - `GET /api/validations/{id}/measures`
    - `POST /api/validations/{id}/measures` — single
    - `POST /api/validations/{id}/measures/batch` — bulk
    - `PUT /api/validations/{id}/measures/{measureId}`
    - `DELETE /api/validations/{id}/measures/{measureId}`
    - `POST /api/validations/{id}/measures/from-template/{templateId}` — instantiate from catalog with `NOT_EXECUTED` status
- Migration strategy:
    - Liquibase migration creates `validation_measure` table.
    - Data migration: existing `validation_results` rows copied with best-effort mapping (`parameter → measureCode`, `expectedValue` becomes both bounds with ±5% spread for backward compat, `conform=true → OK`, `conform=false → OUT_OF_RANGE`).
    - Old controller `/api/validation-results` continues to work but returns `Deprecation: true` header.
- Auto-recompute `status` and `deviationPct` on every insert/update via a service-layer hook.

### Out of scope
- Removing the old `validation_results` table (deferred until Phase 005 closes).
- Frontend updates (separate sub-project).

### Backend deliverables
- Branch `002-validation-measure-refactor`
- Liquibase migrations for table + data migration
- Contract tests on all measure endpoints
- Integration test: create ticket → add 16 measures → verify all `deviationPct` values
- Backward-compat test: legacy `/api/validation-results` still returns 200

### Frontend deliverables
- New model `validation-measure.model.ts` and DTOs `create-validation-measure.dto.ts`, `update-validation-measure.dto.ts`.
- New service `ValidationMeasureService` (`src/app/services/validation-measure.service.ts`) replacing `ValidationResultService`. Old service kept as a thin shim emitting a `console.warn` on every call (encourages migration without breaking screens).
- Refactor of the ticket detail page (`src/app/pages/Ticket/ticket-detail/`):
    - Replace the existing "Results" panel with a new `MeasurePanel` component.
    - Table columns: `measureCode` (via `MeasureBadge`), `measuredValue`, `unit`, `[lowerBound, upperBound]`, `status` (color-coded badge: green `OK`, red `OUT_OF_RANGE`, gray `NOT_EXECUTED`), `deviationPct` (with progress bar — green 0–50%, amber 50–100%, red >100%).
    - "Add measure" dialog: dropdown of catalog templates for the ticket's zone PosteType, pre-fills bounds and unit, only `measuredValue` is editable.
    - "Add ad-hoc measure" advanced dialog (for measures outside the catalog).
    - Bulk-edit mode: edit several measure values in one go, submit via `/measures/batch`.
    - "Instantiate all template measures as NOT_EXECUTED" button — populates the ticket with the full catalog in one click (useful at ticket start).
- New shared component `MeasureStatusBadge` and `DeviationProgress` under `src/app/shared/components/`.
- New pipe `MeasureUnitPipe` to format values like `15.52 dBm`, `35.53 mA` consistently.
- Update ticket-create flow: after creation, automatically call `from-template` to seed the new ticket with `NOT_EXECUTED` measures from its zone catalog.
- Karma tests for `ValidationMeasureService` and `MeasurePanel`.

### Acceptance criteria
- A measure with `measured=15.5`, `lowerBound=13.5`, `upperBound=16.5` has `status=OK` and `deviationPct ≈ 33.3` (½ tolerance away from center).
- A measure with `measured=20.0`, same bounds, has `status=OUT_OF_RANGE` and `deviationPct ≈ 433`.
- A measure with `measured=null` has `status=NOT_EXECUTED`.
- Legacy endpoint emits `Deprecation: true` header.
- Opening any ticket renders the new `MeasurePanel` with color-coded status badges and progress bars; the legacy "Results" panel is removed from the UI.

---

## 8. Phase 003 — Workflow Guard

### Goal
Gate the `EN_COURS → EN_REVUE` transition with a coverage rule: a ticket cannot be submitted for review unless **all mandatory measures from its zone's catalog have a status other than `NOT_EXECUTED`**.

### Why
This is the user's core requirement: "the workflow should not move forward until measurements are real and complete." It elevates the workflow from decorative to business-rule-driven.

### Scope
- New service `TicketTransitionGuard` — single entry point for all transition validation. Each rule is a strategy class implementing `TransitionRule`.
- Concrete rules:
    - `MandatoryMeasureCoverageRule` — for `EN_COURS → EN_REVUE`.
    - (placeholder) `PrepValidationRule`, `RoleAuthorizationRule` — existing rules wired into the guard for consistency.
- DTO `WorkflowReadinessDTO`:
  ```json
  {
    "ticketId": 42,
    "currentStatus": "EN_COURS",
    "targetStatus": "EN_REVUE",
    "mandatoryTotal": 16,
    "mandatoryFilled": 14,
    "mandatoryMissing": 2,
    "missingMeasures": [
      { "measureCode": "POWER_RMS_AVG_VSA1_ANT3_5670", "label": "...", "required": true }
    ],
    "outOfRangeMeasures": [...],
    "canTransition": false,
    "blockingReasons": ["2 mandatory measures still in NOT_EXECUTED state"]
  }
  ```
- Endpoints:
    - `GET /api/validations/{id}/readiness` — non-mutating; front uses it to enable/disable buttons
    - `GET /api/validations/{id}/readiness?targetStatus=EN_REVUE` — explicit target
- Refactor of `PATCH /api/validations/{id}/submit-review`:
    - Call guard first.
    - On block, return HTTP 422 with the `WorkflowReadinessDTO` payload.
    - On success, proceed as today.
- WebSocket integration:
    - On every measure insert/update/delete, push readiness snapshot to `/topic/validation/{id}/readiness`.
    - Front shows live progress bar (out of scope, but contract ready).

### Out of scope
- Frontend readiness bar.
- Guarding other transitions (only `EN_COURS → EN_REVUE` in this phase; `PREP_VALIDEE → EN_COURS` etc. can be added later under same guard).

### Backend deliverables
- Branch `003-workflow-guard`
- Integration tests:
    - happy path: 16/16 mandatory filled → `submit-review` returns 200
    - blocked path: 14/16 mandatory filled → `submit-review` returns 422 with detail
    - readiness endpoint matches mutation result
- Contract test verifying WebSocket message format

### Frontend deliverables
- New model `workflow-readiness.model.ts` matching `WorkflowReadinessDTO`.
- Extension of `TicketService` with `getReadiness(ticketId, targetStatus?)` returning `Observable<WorkflowReadinessDTO>`.
- New component `WorkflowReadinessBar` (`src/app/shared/components/workflow-readiness-bar/`):
    - PrimeNG progress bar showing `mandatoryFilled / mandatoryTotal`.
    - Color: red <50%, amber 50–99%, green 100%.
    - Tooltip lists `missingMeasures[]` with their codes and labels.
    - Click expands a side panel listing missing and out-of-range measures, each clickable to scroll to the corresponding row in `MeasurePanel`.
- Wire-up in ticket detail page:
    - The "Submit for Review" button is `[disabled]` when `canTransition === false`.
    - The button label dynamically shows `Submit for review (14/16)` when blocked.
    - Subscribe to `/topic/validation/{id}/readiness` via `WebSocketService` to update the bar in real time as measures are added.
- Error handling: when the API returns 422 on submit-review, the front catches the `WorkflowReadinessDTO` payload and shows a PrimeNG toast + opens the readiness side panel automatically.
- Karma tests for `WorkflowReadinessBar` rendering under different `canTransition` states.

### Acceptance criteria
- A ticket with one mandatory `NOT_EXECUTED` measure cannot be submitted for review.
- The blocked response identifies exactly which measures are missing.
- Adding the missing measure flips `canTransition` to `true` and emits a WebSocket update.
- The Submit-for-Review button is disabled in the UI as long as `canTransition === false`, and its label live-updates with `(filled/total)` as measures are added.

---

## 9. Phase 004 — Sagemcom Log Importer

### Goal
Parse Sagemcom production log files (`.log` / `.txt`) and auto-populate a ticket's measures from the parsed content, with a dry-run preview mode.

### Why
This is the **demo highlight** for the PFE defense: a technician (or evaluator) drag-drops a real Sagemcom log file → measures appear → readiness flips to 100% → review submission unlocked. It also strengthens defensibility — the parser is tested against real supervisor-provided logs.

### Scope
- New service `SagemcomLogParser`:
    - Strategy-based: `BnftLogStrategy`, `BwcLogStrategy`, `BtfLogStrategy`, with header sniffing (`EZR-AVS*` → BNFT; `EZR-BBS27*` + `BWC` → BWC; `EZR-BBS22*` + `BTF` → BTF).
    - Final-block regex (common across all three formats):
      ```
      Mesure\s+<(?<code>\w+)>\s+:\s+(?<label>[^-]+)-\s+Status\s+(?<status>\d)
      \s+(?<min>[\d.]+)\s+(?<unit>\S+)\s+<\s+\.\.\.\s+<\s+(?<max>[\d.]+)
      (?:\s+(?<value>[\d.]+)\s+\S+)?
      ```
    - For BWC, additional secondary parser for the inline `POWER_RMS_AVG_VSA1` lines `... (lower, upper)`.
- Endpoints:
    - `POST /api/validations/{id}/preview-log` (multipart) — returns a `LogImportReportDTO` without persisting; lets the user review before commit.
    - `POST /api/validations/{id}/import-log` (multipart) — persists matched measures.
- DTO `LogImportReportDTO`:
  ```json
  {
    "detectedFormat": "BWC",
    "totalParsed": 18,
    "matched": [
      { "measureCode": "POWER_RMS_AVG_VSA1", "value": 15.52, "status": "OK", "templateId": 142 }
    ],
    "unmatched": [
      { "measureCode": "POWER_PEAK_AVG_VSA1", "reason": "No matching template in WIFI_CONDUIT catalog" }
    ],
    "warnings": ["Measure SCRATCH_TEST has no unit in catalog; using log unit"]
  }
  ```
- Matching algorithm:
    1. Look up `(posteType, measureCode)` in `PosteMeasureCatalog`.
    2. If not found, try a configurable alias table (e.g., `MES_BNFT_PWR0_2G ≡ PWR_2G_ANT0`).
    3. If still unmatched, record under `unmatched[]` with reason.
- Source-file traceability:
    - Persist the uploaded file under `storage/logs/{validationId}/{originalName}` or a blob column.
    - Set `ValidationMeasure.sourceLogFile` for every imported measure.

### Out of scope
- Reverse export (generating a log file from measures).
- Real-time log tail (file watcher).

### Backend deliverables
- Branch `004-log-importer`
- Three real fixtures under `src/test/resources/fixtures/sagemcom-logs/`:
    - `bnft-decoder-M393.txt` (the first log)
    - `bwc-gateway-safran-wifi5g.log` (the second log)
    - `btf-gateway-fb107-wifi7.log` (the third log)
- Integration tests parsing each fixture and asserting the expected measure count and at least 3 specific measure values per file.
- Negative tests: corrupted log, log from an unsupported station, log with no final block.

### Frontend deliverables — the defense-demo highlight
- New model `log-import-report.model.ts` matching `LogImportReportDTO`.
- Extension of `ValidationMeasureService` with two methods: `previewLog(ticketId, file)` and `importLog(ticketId, file)`, both using `FormData` multipart.
- New component `LogImportDialog` (`src/app/pages/Ticket/log-import-dialog/`):
    - PrimeNG `FileUpload` in drag-drop mode, accepting `.log`, `.txt`, `.zip` (zip support optional, deferred).
    - Step 1 — drop the file → spinner → preview report displayed.
    - Step 2 — preview view: three accordion sections
        - **Matched** (green count): table of measures that will be created, with values and computed status.
        - **Unmatched** (amber count): table showing each unmatched code and reason, with an "Add to catalog" inline action for `ADMIN_IT` / `CHEF_SECTEUR`.
        - **Warnings** (yellow count): plain list.
    - Step 3 — "Confirm import" button → calls `importLog` → success toast → dialog closes → `MeasurePanel` and `WorkflowReadinessBar` refresh automatically.
- Wire-up in ticket detail page:
    - Big "Import Sagemcom log" button next to the measure panel, role-gated to `TECH_VAL`, `TECH_PREP`, `ADMIN_IT`.
    - Button shows a subtle pulse animation when the ticket has 0 measures (visual hint for the demo).
- Detection feedback: the preview report shows a header chip `Detected format: BWC` so the user (and the jury) sees the parser identified the log type.
- Source traceability UI: each measure row in `MeasurePanel` gets a small paperclip icon when `sourceLogFile` is set; hovering shows the original log filename; clicking opens a `LogSourceDialog` showing the snippet of the source log that produced this measure (server returns the snippet via a new endpoint `GET /api/validations/{id}/measures/{measureId}/source-snippet`).
- Karma test of the dialog covering the three fixture types via mocked HTTP.

### Acceptance criteria
- Importing `bwc-gateway-safran-wifi5g.log` into a `WIFI_CONDUIT` ticket populates ≥16 measures, all with `status=OK`.
- Importing the BNFT log into a `TEST_FONCTIONNEL` ticket populates 6 power measures with bounds matching the log content.
- Preview mode produces identical output to import mode (modulo persistence).
- Source file path is persisted on every imported measure.
- A jury member can drag-drop `bwc-gateway-safran-wifi5g.log` into the dialog, see the matched/unmatched preview, confirm, and observe the measure panel and readiness bar populate live, end-to-end, in under 30 seconds.

---

## 10. Phase 005 — Conformity Engine

### Goal
On `submit-review` (and on closure), auto-compute a verdict (`CONFORME` / `NON_CONFORME`) from the measures' statuses, presented to the reviewing role (EXPERT / CHEF_SECTEUR), who confirms or overrides with traceable justification.

### Why
The current closure is purely manual. With real bounded measures, the verdict can be objectively pre-computed. Human authority remains, but every override is auditable — matching industrial quality-audit practice.

### Scope
- New service `ConformityEvaluator`:
    - Verdict = `CONFORME` if every `mandatory` measure has `status == OK`.
    - Verdict = `NON_CONFORME` otherwise, with a list of blocking measures (status `OUT_OF_RANGE` or, edge case, mandatory NOT_EXECUTED that slipped through — defense-in-depth).
- New entity `ConformityReport`:
    - `id`, `validation` (one-to-one), `computedVerdict`, `computedAt`, `blockingMeasures[]` (JSON or join table), `finalVerdict`, `overridden` (boolean), `overrideJustification?`, `closedBy` (FK user), `closedAt`.
- Endpoints:
    - `GET /api/validations/{id}/conformity-report` — current computed verdict (before closure)
    - `PATCH /api/validations/{id}/close` enriched:
        - Request body: `{ "verdict": "CONFORME" | "NON_CONFORME", "overrideJustification": "..." }`
        - If `verdict` matches computed, justification is optional.
        - If `verdict` differs, justification is mandatory (HTTP 400 otherwise).
        - Persist `ConformityReport`.
- Trigger KPI recalc on closure (wire into existing `KPIService`).

### Out of scope
- Anomaly detection / AI-driven conformity insight (deferred to AI phases).
- Multi-stage approval flow (single closure decision in this phase).

### Backend deliverables
- Branch `005-conformity-engine`
- Migrations for `conformity_report` table
- Integration tests:
    - all OK → computed CONFORME → close without override → report persisted
    - one OUT_OF_RANGE mandatory → computed NON_CONFORME → close with override CONFORME + justification → report persisted with `overridden=true`
    - mismatch without justification → 400

### Frontend deliverables
- New model `conformity-report.model.ts` matching `ConformityReport`.
- Extension of `TicketService` with `getConformityReport(ticketId)` and an updated `closeTicket(ticketId, body)` signature accepting `{ verdict, overrideJustification? }`.
- New component `ConformityReportPanel` (`src/app/pages/Ticket/conformity-report-panel/`), visible when the ticket is in `EN_REVUE`, `CONFORME`, or `NON_CONFORME`:
    - Header card showing **computed verdict** and **final verdict** side by side, with an "Overridden" stamp when they differ.
    - Blocking measures table: each row shows `measureCode`, `measuredValue`, `[lower, upper]`, `deviationPct`, and a "Why?" tooltip explaining the rule that failed.
    - When `overridden === true`, an audit card displays `closedBy`, `closedAt`, and the full `overrideJustification` text.
- New component `CloseTicketDialog` (`src/app/pages/Ticket/close-ticket-dialog/`), role-gated `CHEF_SECTEUR`, `EXPERT`, `ADMIN_IT`:
    - Shows the computed verdict prominently.
    - Two big buttons: "Confirm CONFORME" and "Mark NON_CONFORME".
    - When the chosen verdict differs from the computed one, a mandatory `<textarea>` for `overrideJustification` appears (min 20 chars, validator-enforced).
    - Submit disabled until justification is valid (when required).
- Wire-up: the existing "Close ticket" button now opens `CloseTicketDialog` instead of the legacy free-text dialog.
- Karma tests covering the override-required validation and the verdict-side-by-side rendering.

### Acceptance criteria
- Closing a ticket without override and without justification is allowed only when the human verdict equals the computed verdict.
- Override is always traceable to a user, a timestamp, and a justification text.
- Read endpoint returns the same verdict before and after closure.
- The Close Ticket dialog refuses submission with an empty justification when the verdict diverges from the computed one, and shows both verdicts side by side after closure.

---

## 11. Phase 006 — KPIs by Poste & Measure

### Goal
Enrich existing KPI computation with **aggregations by `PosteType`** and **by `measureCode`**, exposing trends actionable by Chef de Secteur and Expert.

### Why
"Conformity rate of line L3" is too coarse. Real questions:
- Which `PosteType` fails most often?
- Which measure code drifts the most over the last 30 days?
- Is the `POWER_RMS_AVG_VSA1` average drifting upward on line L6?

### Scope
- Repository methods (JPQL or native SQL views):
    - Conformity rate per `PosteType` over a date range
    - Top-N most-deviant measure codes (sorted by avg `deviationPct`)
    - Time-series of avg/min/max for a given `measureCode` on a given line
- Endpoints:
    - `GET /api/kpis/by-poste-type?lineId=...&from=...&to=...`
    - `GET /api/kpis/top-deviant-measures?lineId=...&limit=10`
    - `GET /api/kpis/measure-trend?measureCode=POWER_RMS_AVG_VSA1&lineId=...&days=30`
- DTOs `KpiByPosteDTO`, `MeasureDeviationDTO`, `MeasureTrendPointDTO`.
- Caching layer (Caffeine) with TTL 5 min on heavy aggregations; invalidate on ticket closure event.

### Out of scope
- Predictive KPIs (drift forecasting) — that is an AI pillar.
- KPI export (CSV/PDF).

### Backend deliverables
- Branch `006-kpi-by-poste-measure`
- Integration tests over a seeded dataset of ≥50 closed tickets across 3 poste types
- Performance test: each KPI endpoint returns in <300 ms with 1000 measures

### Frontend deliverables
- New models `kpi-by-poste.model.ts`, `measure-deviation.model.ts`, `measure-trend-point.model.ts`.
- Extension of `KpiService` with three methods: `getByPosteType(lineId, from, to)`, `getTopDeviantMeasures(lineId, limit)`, `getMeasureTrend(measureCode, lineId, days)`.
- New tab "Industrial KPIs" inside the existing `kpis` page, role-gated `ADMIN_IT`, `CHEF_SECTEUR`, `EXPERT`, `RESPONSABLE`:
    - **PosteType conformity card grid** — one card per `PosteType` present on the line, showing conformity rate, total tickets, and a sparkline of the last 30 days. Cards are clickable to drill down.
    - **Top-deviant measures table** — sortable table with `measureCode`, `posteType`, `avgDeviationPct`, `occurrences`, and a "View trend" action.
    - **Measure trend chart** — Chart.js line chart with avg/min/max bands; query selector at the top (measure code + line + date range).
- New shared component `SparklineCell` (small Chart.js inline chart) for table reuse.
- Filter bar at the top: production line, date range, optional `PosteType` filter. Filters persist in the URL (query params) so the view is shareable.
- Loading skeletons (PrimeNG `Skeleton`) on all three widgets while data loads.
- Empty states: each widget shows a friendly "No data yet for this filter" panel when applicable.
- Karma tests for `KpiService` and a smoke test rendering the new tab with mocked data.

### Acceptance criteria
- A line with 80% conformity on `TEST_FONCTIONNEL` and 60% on `WIFI_CONDUIT` returns matching rates from `/by-poste-type`.
- Top-deviant endpoint returns measure codes sorted by descending average deviation.
- Trend endpoint returns ordered time-series points.
- The "Industrial KPIs" tab renders the three widgets with seeded data, supports filter persistence via URL params, and updates the trend chart when a measure code is selected from the top-deviant table.

---

## 12. Dependency Graph & Sequencing

```
┌──────────────────────────┐
│ 001 Poste-Type Catalog   │  (foundational, no dependencies)
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ 002 ValidationMeasure    │  (depends on 001 for templates)
│     Refactor             │
└────────┬─────────────────┘
         │
         ├─────────────────────────────┐
         ▼                             ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│ 003 Workflow Guard       │  │ 004 Sagemcom Log         │
│                          │  │     Importer             │
└────────┬─────────────────┘  └────────┬─────────────────┘
         │                             │
         └──────────────┬──────────────┘
                        ▼
              ┌──────────────────────────┐
              │ 005 Conformity Engine    │
              └────────┬─────────────────┘
                       ▼
              ┌──────────────────────────┐
              │ 006 KPIs by Poste &      │
              │     Measure              │
              └──────────────────────────┘
```

**Parallelization opportunity:**
- Within a phase, the **frontend track** of phase N starts as soon as the **backend track** of phase N has frozen DTOs and contracts (typically mid-phase). The frontend can mock responses against the contract until the backend is merged.
- Across phases, **003 and 004 can run in parallel** after 002 is merged, on two separate branches (both backend and frontend).

**Estimated schedule (single developer, ~3–4 days per phase including both tracks):**

| Week | Backend tracks | Frontend tracks |
|---|---|---|
| 1 | 001, start 002 | 001 admin catalog page |
| 2 | finish 002, 003 | 002 measure panel refactor |
| 3 | 004 | 003 readiness bar, 004 log-import dialog |
| 4 | 005, 006 | 005 conformity panel, 006 industrial KPIs tab |

**Demo readiness milestone:** end of week 3 — the drag-drop log import works end-to-end with a real Sagemcom file. From this point on, the project is defendable even if weeks 4 deliverables slip.

---

## 13. Defense Narrative

When defending the PFE, the refactor unlocks four concrete arguments:

1. **Domain immersion.**
   > "I analyzed three real production logs from Sagemcom — BNFT decoder, BWC WiFi gateway, BTF voice gateway — and modeled my `ValidationMeasure` schema directly from the nomenclature used in those logs (`MES_*`, `M_*`, `POWER_*`). My entity isn't generic; it's industrial."

2. **Business-rule-driven workflow.**
   > "My ticket workflow isn't decorative. The `EN_COURS → EN_REVUE` transition is guarded by a coverage rule: for a `WIFI_CONDUIT` ticket, all 16 mandatory power-RMS measures across 4 antennas and 4 frequencies must be filled. Otherwise the API returns 422 with the list of missing measures."

3. **Real-data demo.**
   > "Watch — I drag-drop the actual log file the supervisor gave me into the Angular interface. The parser detects the format (BWC), maps 16 measures to my catalog, the preview shows me what's matched, unmatched, and what warnings to expect. I confirm; the measure panel populates with color-coded badges, the readiness bar jumps from 0/16 to 16/16 live via WebSocket, and the Submit-for-Review button unlocks."

4. **Auditable verdict.**
   > "Conformity isn't a manual checkbox. The engine computes the verdict from the measures. The Close-Ticket dialog presents it to the expert; if they override it, the system forces them to write a justification and persists their identity, the timestamp, and the text. The conformity panel then shows both verdicts side by side with an 'Overridden' stamp — this matches industrial audit practice."

This narrative is independent of AI — when AI pillars come in, they enrich an already-credible substrate, and the UI is already structured with placeholder slots (risk badge area on each ticket, recommendation pane in the detail page) where AI outputs will surface.

---

**End of master plan. Next step: produce the first `spec.md` for Phase 001 under `specs/001-poste-type-catalog/`, covering both the backend and frontend tracks of that phase.**