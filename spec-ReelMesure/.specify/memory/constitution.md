  <!--
SYNC IMPACT REPORT
==================
Version change: (template) → 1.0.0
Bump rationale: Initial ratification — converts the template scaffold into a concrete,
project-specific constitution for the SageLine "ReelMesure" full-stack refactor
(measure-by-measure, bounded-tolerance validation aligned with Sagemcom production logs).
The 12 principles are lifted verbatim from `Plan.md` §5 ("Project Constitution") and
rewritten in declarative MUST/SHALL form so they are testable transition gates.

Modified principles:
  - (none — initial version)
Added sections:
  - Core Principles I–XII
  - Additional Constraints (technology, structure, data, traceability)
  - Development Workflow & Quality Gates (Spec Kit flow, contract & integration testing,
    backward-compat deprecation window, override audit)
  - Governance (amendment procedure, versioning policy, compliance review)
Removed sections:
  - (none — initial version)

Templates requiring updates:
  - ✅ `.specify/memory/constitution.md` (this file)
  - ✅ `.specify/templates/plan-template.md` — generic "Constitution Check" gate is
    compatible; no edits required. Phase plans MUST cite the principles by Roman numeral
    when a deviation is declared.
  - ✅ `.specify/templates/spec-template.md` — generic; no edits required. Specs SHOULD
    use industrial measure nomenclature (Principle I) and bounded `[lowerBound, upperBound]`
    semantics (Principle II) when referencing measures.
  - ✅ `.specify/templates/tasks-template.md` — generic; no edits required. Phase task
    lists MUST include contract tests, real-log fixture integration tests where
    applicable (Principle VII), and role-gating tasks for new UI routes (Principle XII).
  - ✅ `.specify/templates/checklist-template.md` — generic; no edits required.
  - ✅ Root `CLAUDE.md` (backend) — already documents the layered architecture, DTO
    separation, role hierarchy, and ticket workflow that Principles VI, IV, and XII
    rely on; no edits required.

Follow-up TODOs:
  - (none — all placeholders resolved)
-->

# SageLine ReelMesure Constitution

This constitution governs the **SageLine full-stack refactor (`spec-ReelMesure`)** that
realigns the validation workflow with the real Sagemcom production-line measurement
model. It is the immutable contract every phase (001 PosteType Catalog → 006 KPIs by
Poste & Measure) MUST honor. Phase-level deviations are only permitted when explicitly
declared and justified under "Constitution Check" inside that phase's `plan.md`.

## Core Principles

### I. Industrial Fidelity

All measure nomenclature, tolerance semantics, units, and workflow rules MUST mirror the
conventions observed in real Sagemcom production logs (`MES_*`, `M_*`, `POWER_*`, etc.).
Invented domain terms are forbidden. When in doubt, the three supervisor-provided
production logs are the source of truth.

**Rationale:** The defense narrative and the technical value of the refactor both rest
on the claim that SageLine models a real industrial domain end-to-end. Any drift from
Sagemcom nomenclature breaks that claim.

### II. Bounded Tolerance, Not Target

Every measure MUST be validated by a `[lowerBound, upperBound]` pair with an explicit
unit. The legacy single `expectedValue` field is DEPRECATED and MUST NOT be referenced
in new code. Data migrated from the legacy schema is widened to `expectedValue ± 5%`
strictly as a one-time backward-compat shim (Phase 002), not as an ongoing pattern.

**Rationale:** Sagemcom logs validate by range, not by target. A single expected value
cannot represent the industrial reality and produces false conformity verdicts.

### III. Three-Valued Measure Status

Every `ValidationMeasure` MUST carry `MeasureStatus ∈ {OK, OUT_OF_RANGE, NOT_EXECUTED}`,
aligned with Sagemcom's Status `0` / `1` / `2`. Boolean conformity flags are forbidden
on new entities. `NOT_EXECUTED` is a first-class state (used by workflow guards) and
MUST NOT be coerced to `false` or `null` at the API boundary.

### IV. Guarded Transitions (NON-NEGOTIABLE)

No ticket status transition MAY exist without an explicit, tested business rule encoded
in a `TransitionRule` strategy and dispatched by the single `TicketTransitionGuard`
entry point. A blocked transition MUST return HTTP 422 with a `WorkflowReadinessDTO`
payload that names every missing or out-of-range measure. Free transitions are
forbidden.

**Rationale:** This principle is what elevates the workflow from decorative to
business-rule-driven and is the second pillar of the defense narrative.

### V. Traceability from Log to Verdict

Every `ValidationMeasure` MUST be traceable to its origin: either the source log file
(`sourceLogFile` populated, original file persisted under
`storage/logs/{validationId}/`) or the authenticated user who entered it manually
(`enteredBy` FK). The `GET /api/validations/{id}/measures/{measureId}/source-snippet`
endpoint MUST return the originating log fragment when `sourceLogFile` is set.

### VI. DTO / Entity Separation

JPA entities MUST NEVER be exposed in REST responses. Requests use DTOs under
`com.pfe.sageline.dtos.request.*`; responses use DTOs under `dtos.response.*`. Manual
mappers live under `mappers/`. Frontend models in `src/app/models/` mirror response
DTOs one-to-one. Lombok `@Data` on entities is permitted; it is NOT permission to leak
them.

### VII. Real-Log Test Fixtures (NON-NEGOTIABLE)

The three Sagemcom logs provided by the supervisor MUST be committed under
`src/test/resources/fixtures/sagemcom-logs/` and used as integration-test inputs for
the parser (Phase 004) and any phase that depends on parsed measure data. Mocked or
synthetic log content is permitted only for negative tests (corrupted file, unsupported
station, missing final block).

### VIII. Backward Compatibility During Refactor

Any endpoint or entity superseded by this refactor MUST remain available for at least
one phase after its replacement merges, returning HTTP header `Deprecation: true`
(specifically: the legacy `/api/validation-results` controller, deprecated in Phase 002,
removable no earlier than Phase 005 closure). The frontend MUST keep a thin compat shim
that calls the new endpoint first and falls back to the legacy one only on 404. Hard
removal requires a dedicated migration task in `tasks.md`.

### IX. Auditability of Overrides

Whenever a human verdict differs from the computed verdict, the override MUST be
persisted with operator identity (`closedBy`), timestamp (`closedAt`), `overridden=true`,
and a non-empty `overrideJustification` of at least 20 characters. APIs MUST reject
diverging verdicts without justification with HTTP 400. The UI MUST display computed and
final verdicts side by side with an "Overridden" stamp whenever they differ.

### X. No Premature AI Integration

AI pillars (semantic memory, forecasting, RAG, agent) are OUT OF SCOPE for this
refactor. The data model and UI MUST nevertheless remain AI-ready: clear codes, explicit
units, context fields (antenna, frequency, modulation scheme), and reserved placeholder
slots on the ticket detail page for future risk badges and recommendations. No AI calls,
imports, or dependencies SHALL be introduced under the `spec-ReelMesure` branches.

### XI. Frontend Stack Consistency

All new Angular components MUST be NgModule-based (`standalone: false`), declared in
`app.module.ts`, and use:

- the centralized PrimeNG module,
- the `lara-dark-blue` theme,
- the `--sage-*` CSS variables,
- DM Sans (body) / JetBrains Mono (code) fonts,
- the `app` component prefix.

No new UI library, no standalone components, no theme forks. Shared components live
under `src/app/shared/components/` and are reused across phases (e.g., `MeasureBadge`
introduced in Phase 001 is reused in Phases 002–004).

### XII. Role-Gated UI (NON-NEGOTIABLE)

Every new Angular route MUST declare `data: { roles: [...] }` and be protected by
`AuthGuard`. Every destructive or status-changing action MUST be **hidden** (not merely
disabled) for users without the required role. Backend `@PreAuthorize` annotations
remain the primary access-control layer; the UI gating is the user-experience
counterpart. Role mappings MUST use the canonical hierarchy: `ADMIN_IT`, `EXPERT`,
`CHEF_SECTEUR`, `TECH_PREP`, `TECH_VAL`, `RESPONSABLE`.

## Additional Constraints

**Technology stack (frozen for this refactor):**

- Backend: Spring Boot 4.0.2, Java 17, PostgreSQL, Keycloak OAuth2 JWT, Liquibase for
  schema migrations (`V*.sql`), STOMP over WebSocket at `/ws`.
- Frontend: Angular 17, PrimeNG, NgModule architecture, Chart.js for KPI visualizations.
- Package root: `com.pfe.sageline`. Layered structure (Controller → Service →
  Repository → Entity) is mandatory; cross-layer shortcuts are forbidden.

**Data model invariants:**

- `(posteType, measureCode)` MUST be unique in `PosteMeasureCatalog` (DB constraint +
  DTO validator). Duplicate inserts MUST return HTTP 409.
- `MeasureDeviationCalculator` is the single source of truth for `status` and
  `deviationPct`. Both fields MUST be recomputed on every insert/update of a
  `ValidationMeasure`.
- Repository queries MUST use JPQL with `LEFT JOIN FETCH` for relations rendered in DTOs
  to avoid N+1.

**Source-log storage:** Imported `.log` / `.txt` files MUST be persisted on disk under
`storage/logs/{validationId}/{originalName}`. They are referenced (not duplicated) from
`ValidationMeasure.sourceLogFile`.

**KPI performance:** Each KPI endpoint introduced in Phase 006 MUST return in under
300 ms with 1 000 measures. Heavy aggregations MUST be cached via Caffeine (TTL 5 min)
and invalidated on ticket closure.

## Development Workflow & Quality Gates

Every phase follows the GitHub Spec Kit flow on its own feature branch
(`001-...` through `006-...`):

```
/speckit-specify → /speckit-clarify → /speckit-plan → /speckit-tasks → /speckit-implement
```

**Per-phase gates (a phase is DONE only when all hold):**

1. All tasks in `tasks.md` are checked off.
2. Contract tests pass for every new endpoint.
3. Integration tests pass — including the real-log fixture tests where the phase parses
   or consumes log data (Principle VII).
4. `quickstart.md` lets a reviewer reproduce the feature end-to-end in under 5 minutes.
5. "Constitution Check" in `plan.md` is satisfied, or any deviation is explicitly listed
   with a remediation owner and target phase.
6. Backward-compat behavior holds (Principle VIII): superseded endpoints still respond
   and emit the `Deprecation` header for at least one phase.

**Cross-phase rules:**

- Frontend track of phase N MAY start as soon as backend DTOs and contracts are frozen
  for that phase (typically mid-phase). Frontend MAY mock against the contract until the
  backend merges.
- Phases 003 and 004 MAY run in parallel after Phase 002 merges.
- Demo-readiness milestone is the end of Phase 004 (drag-drop Sagemcom log import works
  end-to-end). Phases 005 and 006 enrich but do not block defense readiness.

**Code review checks (every PR):**

- DTO/entity separation (Principle VI) — no entity types in `@RestController` signatures.
- Role gating present on new routes and controller methods (Principle XII).
- Migrations are reversible or have a documented rollback note.
- New measure-related code uses bounded tolerance and `MeasureStatus` (Principles II,
  III); reviewers MUST reject the introduction of new boolean conformity flags.

## Governance

This constitution supersedes ad-hoc conventions, prior CLAUDE.md guidance, and the
generic Spec Kit templates wherever they conflict. The root `CLAUDE.md` files document
the existing codebase; this constitution governs what is added or changed by
`spec-ReelMesure`. In any conflict, this constitution wins for files under
`spec-ReelMesure/` and for any feature branch numbered `001-` through `006-`.

**Amendment procedure.** Amendments are proposed by editing this file via `/speckit-
constitution`, accompanied by a Sync Impact Report (rendered as the leading HTML
comment in this file). An amendment is ratified when the version bump, the rationale,
and the updated principle text are all merged on `master`. Amendments MUST update every
phase plan currently in flight (`Constitution Check` section) within the same PR or in
an immediately following PR with a referenced issue.

**Versioning policy** (semantic):

- **MAJOR** — a principle is removed, redefined in a backward-incompatible way, or a
  Governance rule is rewritten such that previously compliant work would now be
  non-compliant.
- **MINOR** — a new principle or section is added; an existing principle is materially
  expanded with new normative ("MUST" / "SHALL") obligations.
- **PATCH** — wording clarifications, typo fixes, rationale tweaks, non-semantic
  refinements (e.g., adjusting a "SHOULD" example).

**Compliance review.** Every phase's `plan.md` MUST contain a "Constitution Check"
gate evaluated before Phase 0 (research) and re-evaluated after Phase 1 (design).
Violations either MUST be resolved before `/speckit-implement` runs, or MUST be
explicitly accepted in a "Complexity Justification" block citing the violated principle
by Roman numeral. Unjustified violations are a blocker for merge.

**Runtime guidance.** For day-to-day implementation conventions (build commands,
database setup, security wiring, existing ticket workflow), refer to the root
`CLAUDE.md`. This constitution governs *invariants*; `CLAUDE.md` documents *current
state*. When they disagree about a principle covered above, the constitution wins.

**Version**: 1.0.0 | **Ratified**: 2026-05-11 | **Last Amended**: 2026-05-11
