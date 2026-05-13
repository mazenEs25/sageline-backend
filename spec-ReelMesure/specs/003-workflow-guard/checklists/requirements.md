# Specification Quality Checklist: Workflow Guard

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-05-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- Spec uses domain status names (`EN_COURS`, `EN_REVUE`, `NOT_EXECUTED`, `OK`, `KO`, `OUT_OF_TOLERANCE`) and existing role labels (`TECH_VAL`, `CHEF_SECTEUR`, etc.) as established by the project's domain vocabulary; these are not implementation details but the shared business language carried over from Phases 001 and 002.
- Carry-over rules from prior phases (Phase 002 measure-editability guard limiting edits to `EN_COURS`; Phase 001 mandatory-template flag on the catalog) are referenced as assumptions, not redefined here.

## Phase 003 Complete — Test Coverage Map

- **SC-001** (submit blocked when mandatory missing → 422): proven by US1 bundle T022(a) `MandatoryMeasureCoverageRuleTest` + T022(c) `SubmitReviewLifecycleIntegrationTest.blocked_when_two_mandatory_missing`
- **SC-002** (happy path submit → 200 + EN_REVUE): proven by US1 bundle T022(c) `SubmitReviewLifecycleIntegrationTest.succeeds_after_filling_all_mandatory`
- **SC-003** (readiness probe latency ≤ 300ms p95): proven by US2 bundle T025(a) `WorkflowReadinessServiceTest.latency_smoke`
- **SC-004** (probe idempotent, no state mutation): proven by US2 bundle T025(a) `WorkflowReadinessServiceTest.idempotent_reads`
- **SC-005** (STOMP snapshot published after measure mutation): proven by US3 bundle T028 `ReadinessSnapshotStompContractTest`
- **SC-006** (every EN_REVUE transition passes through guard): proven by Polish bundle T031 `SubmitReviewGuardArchitectureTest`
- **SC-007** (probe response == 422 refusal body): proven by US2 bundle T025(a) `probe_equals_refusal_readiness` + T025(c) `ProbeMatchesRefusalIntegrationTest`
- **SC-008** (block → fill → retry → success cycle): proven by US1 bundle T022(c) `SubmitReviewLifecycleIntegrationTest` (blocked_when_two_mandatory_missing + succeeds_after_filling_all_mandatory)
