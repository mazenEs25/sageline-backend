---
description: "Task list for Phase 003 — Workflow Guard (Backend Only)"
---

# Tasks: Workflow Guard (Backend Only)

**Input**: Design documents under `specs/003-workflow-guard/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/workflow-guard-api.openapi.yaml`, `quickstart.md`
**Branch**: `003-workflow-guard` (Phases 001 and 002 already merged on `master`)

**Tests**: Included. Constitution gates #2 and #3 make contract and integration tests mandatory. Per the user's request, **tests are bundled into a small number of large tasks** (one per user story + one polish bundle) instead of being split per class. Each test bundle lists exactly which test classes to create and what each must cover.

## How to read this file (cheap-LLM friendly)

- Every task lists the **exact file path** to create or edit, the **class/method names**, and a **"what to write" outline**. You should not need to invent file paths.
- Execute top-to-bottom. `[P]` = parallelizable (different file, no dependency on incomplete tasks).
- After every task, run `./mvnw clean compile`. After every test bundle, run `./mvnw test`. Commit after each numbered task or logical group.
- All Java code uses the existing `com.pfe.sageline.*` package layout from Phases 001 and 002.
- Existing classes you will reuse without modification (do **not** edit them): `Validation` (entity), `ValidationRepository`, `TicketStatus` (enum), `MeasureStatus` (enum), `PosteMeasureCatalog` (entity, has `mandatory` boolean), `User`, `SecurityUtils`, `JpaAuditingConfig`, `WebSocketConfig`, `SimpMessagingTemplate` (autowire it from Spring), `GlobalExceptionHandler` (you will *add* one `@ExceptionHandler` to it but not change existing ones).
- Existing classes you **will modify** (small, focused edits called out per task): `ValidationMeasure` (one new field), `ValidationMeasureServiceImpl` (stamp + post-commit publish), `ValidationService` (wire guard into `submitForReview` + auto-advance), `GlobalExceptionHandler` (one new handler).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks ⇒ parallelizable.
- **[Story]**: `[US1]`, `[US2]`, `[US3]`. Setup, Foundational, and Polish phases carry no story label.

## Path Conventions

All paths are relative to the Spring Boot project root: `sageLine-backend/`. New files live under existing packages plus one new sub-package `com.pfe.sageline.service.workflow`. No new top-level directories.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the project is in a state where Phase 003 work can begin. No new dependency to install — Flyway, Testcontainers, Spring WebSocket/STOMP, Spring Security Test are already on the path from Phases 001 and 002.

- [x] T001 Verify pre-conditions: from `sageLine-backend/`, run `./mvnw clean compile`. It MUST succeed. Then run `./mvnw spring-boot:run` once and confirm Flyway logs report `V0.1`, `V0.2`, `V1.0`, `V1.1`, `V1.2`, `V1.3`, `V2.0`, `V2.1`, `V2.2` all applied to local `sageLine_db`. Stop the app. If compile fails or any V* migration fails, STOP and surface the error before continuing.
- [x] T002 [P] Open `src/main/resources/application.properties`. Confirm exactly: `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration`. Do NOT change anything.
- [x] T003 [P] Open `pom.xml`. Confirm dependencies present: `spring-boot-starter-websocket`, `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. Do NOT add or remove anything.

**Checkpoint**: Compile clean, schema at V2.2, dependencies present. Phase 003 work can start.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add the `mandatory_at_creation` snapshot column on `validation_measures`, backfill it, expose it on the entity, stamp it at write time. After this phase, the data the guard reads exists; the guard itself is not yet wired.

**⚠️ CRITICAL**: No US-labelled task may begin before this phase is complete.

- [x] T004 Create Flyway migration `src/main/resources/db/migration/V3.0__validation_measure_mandatory_snapshot.sql` with **exactly** this content:

  ```sql
  ALTER TABLE validation_measures
      ADD COLUMN mandatory_at_creation BOOLEAN NOT NULL DEFAULT FALSE;

  UPDATE validation_measures vm
  SET    mandatory_at_creation = COALESCE(c.mandatory, FALSE)
  FROM   poste_measure_catalog c
  WHERE  vm.catalog_template_id = c.id;

  CREATE INDEX idx_vm_validation_mandatory
      ON validation_measures (validation_id, mandatory_at_creation);
  ```

  Do not change anything else in `db/migration/`. After saving, run `./mvnw spring-boot:run` to confirm Flyway applies V3.0 cleanly, then stop the app.

- [x] T005 Edit `src/main/java/com/pfe/sageline/entity/ValidationMeasure.java`. Add this field **immediately after the `updatedAt` field** (so it is the last field in the class):

  ```java
  @Column(name = "mandatory_at_creation", nullable = false)
  private boolean mandatoryAtCreation;
  ```

  Lombok's `@Getter`/`@Setter`/`@Builder` annotations on the class generate the accessor and builder method automatically — do not write them. Do not change any other field. Run `./mvnw clean compile` to confirm `validate` mode accepts the new column.

- [x] T006 Edit `src/main/java/com/pfe/sageline/service/ValidationMeasureServiceImpl.java`. In every place a `ValidationMeasure` is **constructed for the first time** (look for `ValidationMeasure.builder()` calls in the methods `create(...)` at L56, `batchCreate(...)` at L206, and any catalog-instantiation method like `instantiateFromCatalog`/`instantiateMissing`), set `.mandatoryAtCreation(...)` on the builder, with this value:

  - If the builder is being populated from a `PosteMeasureCatalog template` (i.e., a catalog-linked row), set `.mandatoryAtCreation(template.isMandatory())` — fall back to `false` if `template.getMandatory() == null` (defensive; the column is `NOT NULL`).
  - If the builder is being populated from a `CreateMeasureRequest` with no catalog template (ad-hoc measure), set `.mandatoryAtCreation(false)`.

  Do **not** add `mandatoryAtCreation` to `CreateMeasureRequest`, `UpdateMeasureRequest`, or `ValidationMeasureResponse`. The field is internal and never crosses the REST boundary in this phase. Do not modify the `update(...)` or `delete(...)` methods — the snapshot is set on insert only and never re-stamped.

- [x] T007 Run `./mvnw spring-boot:run` once and confirm: app starts cleanly, Flyway shows V3.0 already applied (from T004), no startup errors. Stop the app. Then run `./mvnw clean compile` — must succeed.

**Checkpoint**: `validation_measures.mandatory_at_creation` exists, all existing rows are backfilled, every newly inserted measure populates it correctly. The guard now has a stable snapshot to read.

---

## Phase 3: User Story 1 — Block submit-for-review when mandatory measures missing (Priority: P1) 🎯 MVP

**Goal**: A submit-for-review request against an `EN_COURS` ticket with one or more mandatory measures still in `NOT_EXECUTED` is refused with HTTP 422 + `WorkflowReadinessDTO`. Happy path (full coverage) succeeds with HTTP 200 as before.

**Independent Test**: Quickstart steps 1–6 (a 14/16 ticket returns 422 with two named missing measures; recording them flips the response to 200 and the ticket reaches `EN_REVUE`).

### Implementation for User Story 1

- [x] T008 [P] [US1] Create `src/main/java/com/pfe/sageline/dtos/response/MissingMeasureDTO.java`. A `public record MissingMeasureDTO(String measureCode, String label, boolean required) {}` in package `com.pfe.sageline.dtos.response`. No annotations, no other methods.

- [x] T009 [P] [US1] Create `src/main/java/com/pfe/sageline/dtos/response/OutOfRangeMeasureDTO.java`. A `public record OutOfRangeMeasureDTO(String measureCode, String label, Double measuredValue, Double lowerBound, Double upperBound, Double deviationPct) {}` in the same package.

- [x] T010 [P] [US1] Create `src/main/java/com/pfe/sageline/dtos/response/WorkflowReadinessDTO.java`:

  ```java
  package com.pfe.sageline.dtos.response;
  import java.util.List;
  public record WorkflowReadinessDTO(
      Long ticketId,
      String currentStatus,
      String targetStatus,
      int mandatoryTotal,
      int mandatoryFilled,
      int mandatoryMissing,
      List<MissingMeasureDTO> missingMeasures,
      List<OutOfRangeMeasureDTO> outOfRangeMeasures,
      boolean canTransition,
      List<String> blockingReasons
  ) {}
  ```

- [x] T011 [P] [US1] Create `src/main/java/com/pfe/sageline/dtos/internal/MandatoryCoverageRow.java`. Add the new package directory `dtos/internal/` if missing.

  ```java
  package com.pfe.sageline.dtos.internal;
  import com.pfe.sageline.enums.MeasureStatus;
  public record MandatoryCoverageRow(boolean mandatory, MeasureStatus status, long count) {}
  ```

  This is an internal projection carrier — never returned from a controller.

- [x] T012 [US1] Edit `src/main/java/com/pfe/sageline/repository/ValidationMeasureRepository.java`. Add **two** new query methods at the end of the interface (do not touch existing ones):

  ```java
  @org.springframework.data.jpa.repository.Query("""
         SELECT new com.pfe.sageline.dtos.internal.MandatoryCoverageRow(
                vm.mandatoryAtCreation,
                vm.status,
                COUNT(vm))
         FROM   ValidationMeasure vm
         WHERE  vm.validation.id = :validationId
         GROUP  BY vm.mandatoryAtCreation, vm.status
         """)
  java.util.List<com.pfe.sageline.dtos.internal.MandatoryCoverageRow> coverageSummary(
          @org.springframework.data.repository.query.Param("validationId") Long validationId);

  @org.springframework.data.jpa.repository.Query("""
         SELECT new com.pfe.sageline.dtos.response.MissingMeasureDTO(
                vm.measureCode, vm.measureLabel, true)
         FROM   ValidationMeasure vm
         WHERE  vm.validation.id      = :validationId
           AND  vm.mandatoryAtCreation = true
           AND  vm.status              = com.pfe.sageline.enums.MeasureStatus.NOT_EXECUTED
         ORDER  BY vm.measureCode
         """)
  java.util.List<com.pfe.sageline.dtos.response.MissingMeasureDTO> missingMandatoryMeasures(
          @org.springframework.data.repository.query.Param("validationId") Long validationId);

  @org.springframework.data.jpa.repository.Query("""
         SELECT new com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO(
                vm.measureCode, vm.measureLabel, vm.measuredValue,
                vm.lowerBound, vm.upperBound, vm.deviationPct)
         FROM   ValidationMeasure vm
         WHERE  vm.validation.id = :validationId
           AND  vm.status        = com.pfe.sageline.enums.MeasureStatus.OUT_OF_RANGE
         ORDER  BY vm.measureCode
         """)
  java.util.List<com.pfe.sageline.dtos.response.OutOfRangeMeasureDTO> outOfRangeMeasures(
          @org.springframework.data.repository.query.Param("validationId") Long validationId);
  ```

  Use the fully qualified imports inline as shown — keeps the diff small.

- [x] T013 [P] [US1] Create the new package directory `src/main/java/com/pfe/sageline/service/workflow/`. Add file `RuleVerdict.java`:

  ```java
  package com.pfe.sageline.service.workflow;
  import java.util.List;
  public record RuleVerdict(boolean allowed, List<String> blockingReasons) {
      public static RuleVerdict allow() { return new RuleVerdict(true, List.of()); }
      public static RuleVerdict block(String... reasons) { return new RuleVerdict(false, List.of(reasons)); }
  }
  ```

- [x] T014 [P] [US1] Create `src/main/java/com/pfe/sageline/service/workflow/TransitionRule.java`:

  ```java
  package com.pfe.sageline.service.workflow;
  import com.pfe.sageline.entity.Validation;
  import com.pfe.sageline.enums.TicketStatus;
  public interface TransitionRule {
      RuleVerdict evaluate(Validation ticket, TicketStatus targetStatus);
  }
  ```

- [x] T015 [P] [US1] Create `src/main/java/com/pfe/sageline/service/workflow/SourceStatusRule.java`. A `@Component` that returns `RuleVerdict.allow()` when `ticket.getStatus() == EN_COURS` and `targetStatus == EN_REVUE`, otherwise returns `RuleVerdict.block("Ticket source status " + ticket.getStatus() + " is not eligible for transition to " + targetStatus)`. Special-case: if `ticket.getStatus() == EN_ATTENTE_HANDOVER`, return `allow()` (the legacy FR-006a allowance — the original-tech check is enforced by the adapter in T016, not here).

- [x] T016 [P] [US1] Create `src/main/java/com/pfe/sageline/service/workflow/MandatoryMeasureCoverageRule.java`. A `@Component` constructor-injected with `ValidationMeasureRepository`. Implementation:

  ```java
  @Override
  public RuleVerdict evaluate(Validation ticket, TicketStatus targetStatus) {
      if (targetStatus != TicketStatus.EN_REVUE) return RuleVerdict.allow();
      long missing = repository.coverageSummary(ticket.getId()).stream()
              .filter(r -> r.mandatory() && r.status() == MeasureStatus.NOT_EXECUTED)
              .mapToLong(MandatoryCoverageRow::count)
              .sum();
      return missing == 0
          ? RuleVerdict.allow()
          : RuleVerdict.block(missing + " mandatory measures still in NOT_EXECUTED state");
  }
  ```

  Use `import` lines for `MeasureStatus`, `TicketStatus`, `Validation`, `ValidationMeasureRepository`, `MandatoryCoverageRow`. No other logic.

- [x] T017 [US1] Create `src/main/java/com/pfe/sageline/service/workflow/WorkflowReadinessService.java`. A `@Service` (no `@Transactional` — read-only, runs inside caller's transaction) with constructor-injected `ValidationRepository`, `ValidationMeasureRepository`, `SourceStatusRule`, `MandatoryMeasureCoverageRule`. One public method:

  ```java
  public WorkflowReadinessDTO computeReadiness(Long validationId, TicketStatus targetStatus) {
      Validation ticket = validationRepository.findById(validationId)
          .orElseThrow(() -> new ResourceNotFoundException("Validation " + validationId + " not found"));
      TicketStatus target = (targetStatus != null) ? targetStatus : TicketStatus.EN_REVUE;

      List<MandatoryCoverageRow> rows = measureRepository.coverageSummary(validationId);
      int mandatoryTotal  = (int) rows.stream().filter(MandatoryCoverageRow::mandatory).mapToLong(MandatoryCoverageRow::count).sum();
      int mandatoryFilled = (int) rows.stream().filter(r -> r.mandatory() && r.status() != MeasureStatus.NOT_EXECUTED).mapToLong(MandatoryCoverageRow::count).sum();
      int mandatoryMissing = mandatoryTotal - mandatoryFilled;

      List<MissingMeasureDTO> missing = (mandatoryMissing == 0)
          ? List.of()
          : measureRepository.missingMandatoryMeasures(validationId);
      List<OutOfRangeMeasureDTO> outOfRange = measureRepository.outOfRangeMeasures(validationId);

      List<String> reasons = new ArrayList<>();
      RuleVerdict v1 = sourceStatusRule.evaluate(ticket, target);     if (!v1.allowed()) reasons.addAll(v1.blockingReasons());
      RuleVerdict v2 = coverageRule.evaluate(ticket, target);         if (!v2.allowed()) reasons.addAll(v2.blockingReasons());
      boolean canTransition = reasons.isEmpty();

      return new WorkflowReadinessDTO(
          validationId, ticket.getStatus().name(), target.name(),
          mandatoryTotal, mandatoryFilled, mandatoryMissing,
          missing, outOfRange, canTransition, List.copyOf(reasons));
  }
  ```

  Notes for the implementer: do NOT call legacy role / handover / prep checks here — those still live in `ValidationService.submitForReview` and stay there per the Q5 delegate-and-wrap clarification. The readiness service is the single producer of the DTO; the guard (T019) wraps it for the refusal path.

- [x] T018 [US1] Create `src/main/java/com/pfe/sageline/exception/TransitionBlockedException.java`:

  ```java
  package com.pfe.sageline.exception;
  import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
  public class TransitionBlockedException extends RuntimeException {
      private final transient WorkflowReadinessDTO readiness;
      public TransitionBlockedException(WorkflowReadinessDTO readiness) {
          super("Transition blocked: " + String.join("; ", readiness.blockingReasons()));
          this.readiness = readiness;
      }
      public WorkflowReadinessDTO getReadiness() { return readiness; }
  }
  ```

- [x] T019 [US1] Create `src/main/java/com/pfe/sageline/service/workflow/TicketTransitionGuard.java`. A `@Service` constructor-injected with `WorkflowReadinessService`. One method:

  ```java
  public void check(Long validationId, TicketStatus targetStatus) {
      WorkflowReadinessDTO readiness = readinessService.computeReadiness(validationId, targetStatus);
      if (!readiness.canTransition()) throw new TransitionBlockedException(readiness);
  }
  ```

  This is the single funnel demanded by FR-009. Do not add more methods.

- [x] T020 [US1] Edit `src/main/java/com/pfe/sageline/exception/GlobalExceptionHandler.java`. Add a new `@ExceptionHandler(TransitionBlockedException.class)` method **without touching any existing handler**. Place it next to the existing `MeasureNotEditableException` handler for consistency:

  ```java
  @org.springframework.web.bind.annotation.ExceptionHandler(com.pfe.sageline.exception.TransitionBlockedException.class)
  public org.springframework.http.ResponseEntity<com.pfe.sageline.dtos.response.WorkflowReadinessDTO>
          handleTransitionBlocked(com.pfe.sageline.exception.TransitionBlockedException ex) {
      return org.springframework.http.ResponseEntity
              .status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)  // 422
              .body(ex.getReadiness());
  }
  ```

  Use fully qualified names inline so you don't have to manage import order in the existing file.

- [x] T021 [US1] Edit `src/main/java/com/pfe/sageline/service/ValidationService.java`. Constructor-inject the new bean: add `private final TicketTransitionGuard transitionGuard;` to the field list (placement: just before the existing `validationMapper` field, by convention). The class is already `@RequiredArgsConstructor` (Lombok) — Spring will pick it up. Then in `submitForReview(Long id)` at L422, **insert one line immediately after `assertStatus(validation, TicketStatus.EN_COURS, "soumettre pour revue");` (currently L440) and immediately before `validation.setStatus(TicketStatus.EN_REVUE);` (currently L442)**:

  ```java
  transitionGuard.check(validation.getId(), TicketStatus.EN_REVUE);
  ```

  Do **not** remove the existing `assertStatus(...)` call — it stays as a defense-in-depth check. Do not change any other code in this method.

  Add the import: `import com.pfe.sageline.service.workflow.TicketTransitionGuard;`.

### Tests bundle for User Story 1

- [x] T022 [US1] **Test bundle US1** — create the following four test files in one task. Each focuses on a distinct concern; they share no state. Run `./mvnw test` after creating all four to confirm they pass.

  **(a)** `src/test/java/com/pfe/sageline/service/workflow/MandatoryMeasureCoverageRuleTest.java` — pure unit test (no `@SpringBootTest`); constructs the rule with a Mockito-mocked `ValidationMeasureRepository`. Cases:
  - rule returns `allow()` when `targetStatus != EN_REVUE` (use `EN_COURS` as target);
  - rule returns `allow()` when `coverageSummary` returns rows with `mandatory=true, status=OK, count=16` (no missing);
  - rule returns `allow()` when `coverageSummary` returns an empty list (zero-mandatory edge case from spec);
  - rule returns `block(...)` with reason text containing `"2 mandatory measures still in NOT_EXECUTED state"` when summary returns `(true, NOT_EXECUTED, 2)` plus `(true, OK, 14)`;
  - rule **ignores** non-mandatory NOT_EXECUTED rows (passes `(false, NOT_EXECUTED, 5)` along with all-OK mandatory rows → still allow).

  **(b)** `src/test/java/com/pfe/sageline/service/workflow/TicketTransitionGuardTest.java` — pure unit test; constructs the guard with a Mockito-mocked `WorkflowReadinessService`. Cases:
  - given a readiness DTO with `canTransition=true`, `check(...)` returns normally (no exception);
  - given a readiness DTO with `canTransition=false` and `blockingReasons=["X","Y"]`, `check(...)` throws `TransitionBlockedException` whose `.getReadiness()` is the same DTO instance and whose `.getMessage()` contains both `"X"` and `"Y"`.

  **(c)** `src/test/java/com/pfe/sageline/integration/SubmitReviewLifecycleIntegrationTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` + `@Testcontainers` with the project's existing Postgres container helper (copy the same setup pattern Phase 002 uses in its `*IntegrationTest` files — there must be a `BaseIntegrationTest` or `@DynamicPropertySource` block already; reuse it). Seed a `WIFI_CONDUIT` ticket in `EN_COURS` whose catalog instantiation produces 16 mandatory measures (reuse Phase 002's `instantiateFromCatalog` flow via the service layer to do this — do NOT hand-craft the rows). Use `@WithMockUser(roles = "TECH_VAL")` for all calls. Cases (one `@Test` method each):
  - `blocked_when_two_mandatory_missing`: leaves 2 measures as NOT_EXECUTED, 14 as OK; `PATCH /api/validations/{id}/submit-review` → expect HTTP 422; assert response JSON has `canTransition=false`, `mandatoryTotal=16`, `mandatoryFilled=14`, `mandatoryMissing=2`, `missingMeasures` array of size 2 with non-null `measureCode`s; assert ticket is still `EN_COURS` after call.
  - `succeeds_after_filling_all_mandatory`: from the same seed, also fill the 2 remaining mandatory; `PATCH .../submit-review` → expect HTTP 200; assert ticket status in DB is `EN_REVUE`; the previous block-then-pass cycle covers SC-008.
  - `succeeds_when_zero_mandatory_in_catalog`: seed a ticket whose catalog has all-non-mandatory templates (override with one builder call); `PATCH .../submit-review` → 200; ticket → `EN_REVUE`.
  - `wrong_source_status_returns_422`: seed a ticket already in `PLANIFIE`; `PATCH .../submit-review` → 422; assert `blockingReasons[0]` contains the substring `"is not eligible for transition to EN_REVUE"`; ticket unchanged.

  **(d)** `src/test/java/com/pfe/sageline/migration/ValidationMeasureMandatorySnapshotMigrationTest.java` — `@DataJpaTest` (uses Flyway by default since `ddl-auto=validate`) with Testcontainers Postgres. Seed before V3.0 by inserting two `validation_measures` rows directly via JDBC: one with a `catalog_template_id` pointing to a `mandatory=true` catalog row, one with `catalog_template_id=NULL` (ad-hoc). Re-run Flyway via `Flyway.configure()...migrate()` if needed (or rely on the autoconfigured one — pick whichever matches the existing pattern in Phase 002's `LegacyMeasureMigrationIntegrationTest`). Assert: the catalog-linked row has `mandatory_at_creation=true` after migration; the ad-hoc row has `mandatory_at_creation=false`.

  Implementation hint to keep token cost down: copy the harness boilerplate (Testcontainers `@DynamicPropertySource`, JWT/`@WithMockUser` setup, repository helpers) from the Phase 002 file with the closest matching name; do not invent new test infrastructure.

**Checkpoint** ✅ **MVP**: Story 1 fully functional. A blocked submit returns 422+DTO. A clean submit still returns 200. The migration is verified. Stop and demo here if needed.

---

## Phase 4: User Story 2 — Inspect transition readiness without attempting the transition (Priority: P2)

**Goal**: `GET /api/validations/{id}/readiness[?targetStatus=EN_REVUE]` returns the same `WorkflowReadinessDTO` shape used in 422 refusals, never mutates state, and inherits ticket-read authorization.

**Independent Test**: Quickstart steps 1 and 7 — probe a 14/16 ticket and confirm 200 with the expected counts; probe an `EN_REVUE` ticket and confirm 200 with `canTransition=false` and a source-status blocking reason.

### Implementation for User Story 2

- [x] T023 [US2] Create `src/main/java/com/pfe/sageline/controller/WorkflowReadinessController.java`:

  ```java
  package com.pfe.sageline.controller;
  import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
  import com.pfe.sageline.enums.TicketStatus;
  import com.pfe.sageline.service.workflow.WorkflowReadinessService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.web.bind.annotation.*;

  @RestController
  @RequestMapping("/api/validations")
  @RequiredArgsConstructor
  public class WorkflowReadinessController {

      private final WorkflowReadinessService readinessService;

      @GetMapping("/{id}/readiness")
      @PreAuthorize("isAuthenticated()")
      public ResponseEntity<WorkflowReadinessDTO> readiness(
              @PathVariable Long id,
              @RequestParam(value = "targetStatus", required = false) TicketStatus targetStatus) {
          return ResponseEntity.ok(readinessService.computeReadiness(id, targetStatus));
      }
  }
  ```

  Notes: `@PreAuthorize("isAuthenticated()")` is intentional — the production-line scoping comes from `WorkflowReadinessService` reading the ticket via `validationRepository.findById`, and the existing `ResourceNotFoundException` is mapped to 404 by `GlobalExceptionHandler`. If your codebase has a richer ticket-access helper (e.g., `securityUtils.assertCanReadValidation(id)`), call it inside the readiness service before the repository read; otherwise the 404 path is acceptable for this phase. Per spec FR-005 + Q4, the `targetStatus` query param is optional and defaults to `EN_REVUE` inside the service.

- [x] T024 [US2] Edit `src/main/java/com/pfe/sageline/Config/SecurityConfig.java`. Verify (do **not** add) that `/api/validations/**` is reachable by all authenticated users — there should already be a rule like `.requestMatchers("/api/validations/**").authenticated()` from prior phases. If a more restrictive matcher exists (e.g., role list), add an `.requestMatchers(HttpMethod.GET, "/api/validations/*/readiness").authenticated()` line **above** it. Do not change any other line.

### Tests bundle for User Story 2

- [x] T025 [US2] **Test bundle US2** — create three test files in one task. Run `./mvnw test`.

  **(a)** `src/test/java/com/pfe/sageline/service/workflow/WorkflowReadinessServiceTest.java` — `@SpringBootTest` + Testcontainers (reuse Phase 002 harness). Seed a 16-mandatory ticket via `instantiateFromCatalog`. Cases:
  - `idempotent_reads`: take a snapshot of `validations`, `validation_measures`, and any audit columns, call `computeReadiness(...)` 5 times, snapshot again, assert byte-equivalence (SC-004).
  - `probe_equals_refusal`: leave 2 NOT_EXECUTED. Call `computeReadiness(id, EN_REVUE)` and call the same via `TicketTransitionGuard.check(id, EN_REVUE)` (which throws); assert the thrown exception's `.getReadiness()` equals (record equality) the directly-returned DTO (SC-007).
  - `latency_smoke`: with all 16 measures present (mix of OK/OUT_OF_RANGE/NOT_EXECUTED), invoke `computeReadiness` 50 times and assert the median wall-clock under 300 ms in the test JVM. Use `System.nanoTime()`. This is a smoke check, not an SLA — flaky-allow with 1× retry; the goal is to catch obvious N+1 regressions.
  - `non_en_cours_ticket_returns_can_transition_false`: seed a ticket in `EN_REVUE`; assert response has `canTransition=false`, `currentStatus="EN_REVUE"`, `blockingReasons[0]` contains `"is not eligible for transition to EN_REVUE"`. (R-007)

  **(b)** `src/test/java/com/pfe/sageline/controller/WorkflowReadinessControllerTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`. Cases:
  - `200_happy_path`: `@WithMockUser(roles = "TECH_VAL")`; existing seeded ticket; `GET /api/validations/{id}/readiness` → 200; JSON matches the contract schema (assert presence of all 10 top-level fields by `jsonPath`).
  - `200_with_explicit_target_status`: same call with `?targetStatus=EN_REVUE`; assert response `targetStatus` field equals `"EN_REVUE"`.
  - `404_for_missing_ticket`: `GET /api/validations/9999999/readiness` → 404 (mapped by existing handler for `ResourceNotFoundException`).
  - `401_when_unauthenticated`: same call without `@WithMockUser` → 401.

  **(c)** `src/test/java/com/pfe/sageline/integration/ProbeMatchesRefusalIntegrationTest.java` — end-to-end MockMvc test. Seed a 14/16 ticket. Hit `GET .../readiness` with TECH_VAL → capture body JSON. Hit `PATCH .../submit-review` with same TECH_VAL → assert 422 and assert response body JSON **string-equals** the readiness body captured above (after normalization — both flow through Jackson, so a direct `objectMapper.readTree(...)` equality check is sufficient). This is the wire-level twin of SC-007.

**Checkpoint**: Stories 1 and 2 fully functional. The probe lets the front end pre-disable the submit button; the same payload is the 422 envelope.

---

## Phase 5: User Story 3 — Live readiness updates (Priority: P3)

**Goal**: After every successful create / update / delete / batch-create on a measure, a fresh `WorkflowReadinessDTO` for that ticket is published to STOMP topic `/topic/validation.{id}.readiness`. Cross-ticket isolation per FR-011.

**Independent Test**: Quickstart steps 3–5 — subscribe a STOMP client, mutate one measure, observe exactly one snapshot with the expected counts; verify a parallel subscriber on a different ticket id receives nothing.

### Implementation for User Story 3

- [x] T026 [US3] Edit `src/main/java/com/pfe/sageline/service/ValidationMeasureServiceImpl.java`. Constructor-inject two new beans: `private final SimpMessagingTemplate messagingTemplate;` and `private final WorkflowReadinessService readinessService;`. (Class is `@RequiredArgsConstructor` from Phase 002, so just add the fields.)

  Add a single private helper at the bottom of the class:

  ```java
  private void publishReadinessAfterCommit(Long validationId) {
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
          new org.springframework.transaction.support.TransactionSynchronization() {
              @Override public void afterCommit() {
                  try {
                      WorkflowReadinessDTO snapshot = readinessService.computeReadiness(validationId, null);
                      messagingTemplate.convertAndSend(
                          "/topic/validation." + validationId + ".readiness", snapshot);
                  } catch (Exception e) {
                      log.warn("Readiness STOMP push failed for ticket {}: {}", validationId, e.getMessage());
                  }
              }
          });
  }
  ```

  Add the imports: `org.springframework.messaging.simp.SimpMessagingTemplate`, `com.pfe.sageline.dtos.response.WorkflowReadinessDTO`, `com.pfe.sageline.service.workflow.WorkflowReadinessService`.

  Then call `publishReadinessAfterCommit(validationId);` as the **last line** (just before the `return` statement) of each of these public methods: `create(...)` (L56), `update(...)` (L170), `delete(...)` (L196), `batchCreate(...)` (L206). Per R-006, registering inside the `@Transactional` method ensures `afterCommit` only fires on successful commit.

  Per spec edge case (bulk import) and Plan §8 STOMP design: `batchCreate` calls the helper **once** with the validation id (one snapshot per batch — coalesced fanout permitted by spec).

- [x] T027 [US3] Verify the existing `WebSocketConfig` already enables a broker on `/topic`. Open `src/main/java/com/pfe/sageline/Config/WebSocketConfig.java`. There must be an `enableSimpleBroker("/topic", ...)` or similar registration. Do **not** modify the file — just confirm. The new `/topic/validation.{id}.readiness` paths are valid because `/topic` is already a registered prefix.

### Tests bundle for User Story 3

- [x] T028 [US3] **Test bundle US3** — one `@SpringBootTest(webEnvironment = RANDOM_PORT)` test file: `src/test/java/com/pfe/sageline/integration/ReadinessSnapshotStompContractTest.java`. Use `WebSocketStompClient` with `StandardWebSocketClient`, an `org.springframework.messaging.converter.MappingJackson2MessageConverter`, and `LocalValidatorFactoryBean`-style setup (copy the harness from any existing STOMP test in the codebase if present; otherwise, a minimal example: `new WebSocketStompClient(new StandardWebSocketClient())` + `setMessageConverter(...)` + `connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})`). Cases:
  - `single_measure_update_fires_one_snapshot`: subscribe to `/topic/validation.{id}.readiness`; trigger `validationMeasureService.update(...)` to set a NOT_EXECUTED measure to a numeric value; await a snapshot via a `BlockingQueue<WorkflowReadinessDTO>` (5-second timeout); assert exactly one message arrived; assert `mandatoryFilled` is one higher than the pre-update count.
  - `batchCreate_fires_one_coalesced_snapshot`: subscribe; trigger `batchCreate` of 3 measures; await up to 5 seconds; assert **exactly one** message arrived (coalesced — per spec edge case).
  - `cross_ticket_isolation`: subscribe to `/topic/validation.A.readiness`; trigger an update on ticket B; assert no message arrives on the A queue within 2 seconds (FR-011). (Use `pollFirst(2, SECONDS)` returning null.)
  - `delete_fires_snapshot_with_decremented_count`: pre-fill a measure (say count goes from 14→15), then `delete` it; await the snapshot; assert `mandatoryFilled` decremented by one.

  Implementation hints: timeouts are `BlockingQueue#poll(long, TimeUnit)`; deserialize incoming JSON via the `MappingJackson2MessageConverter` registered on the client; use `session.send("/", new byte[0])` is NOT needed — only subscribe + observe. If the codebase has no prior STOMP test to copy from, a working minimal handler is in the Spring `WebSocketStompClient` JavaDoc — keep it short (≤ 30 lines).

**Checkpoint**: All three user stories functional and independently testable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Wire the guard into the auto-advance code path (FR-009 / SC-006), add the architecture test that prevents future regressions, and run the quickstart end-to-end.

- [x] T029 Edit `src/main/java/com/pfe/sageline/service/ValidationService.java` again, this time the auto-advance branch in the poste-closure method (the block that starts around L626 with the comment `// Auto-advance the parent ticket to EN_REVUE once every poste is done.` and ends around L646). Replace the `if (total > 0 && done == total && validation.getStatus() == TicketStatus.EN_COURS) {` block's body so that the `validation.setStatus(TicketStatus.EN_REVUE);` line is preceded by a guarded check that **logs and skips** on block (per R-003 — does NOT throw):

  ```java
  if (total > 0 && done == total
          && validation.getStatus() == TicketStatus.EN_COURS) {
      try {
          transitionGuard.check(validation.getId(), TicketStatus.EN_REVUE);
      } catch (com.pfe.sageline.exception.TransitionBlockedException ex) {
          log.warn("Auto-advance to EN_REVUE skipped for ticket {} — {}",
                  validation.getTicketCode(), ex.getMessage());
          // fall through; do NOT change the ticket status
          posteStatusRepository.save(row);
          return; // or restructure to skip the if-body; preserve original return shape
      }
      validation.setStatus(TicketStatus.EN_REVUE);
      validationRepository.save(validation);
      // ...rest of the existing block unchanged...
  }
  ```

  Important: preserve the existing method's return value contract. The `return` example above is illustrative — fit it to the current method shape (read it carefully before editing). The point is: blocked auto-advance must NOT raise a 422 on the user who was closing a poste.

- [x] T030 Create `src/test/java/com/pfe/sageline/integration/AutoAdvanceGuardedIntegrationTest.java`. `@SpringBootTest` + Testcontainers. Seed a multi-poste ticket whose catalog has at least one mandatory measure. Walk the poste-closure flow until the **last** poste is closed; assert: ticket status remains `EN_COURS` (NOT `EN_REVUE`), no exception thrown to the caller, the closure call returns normally. Then fill the missing mandatory measure and re-trigger the same flow (or use `submitForReview` directly) → ticket reaches `EN_REVUE`.

- [x] T031 [P] Create `src/test/java/com/pfe/sageline/architecture/SubmitReviewGuardArchitectureTest.java`. A pure JUnit 5 test (no Spring context) that uses `Files.walk` over `src/main/java/com/pfe/sageline/` and asserts: the only file containing the literal string `setStatus(TicketStatus.EN_REVUE)` is `ValidationService.java`, and within that file every line containing that literal string is preceded (within 8 lines above) by a `transitionGuard.check(` invocation. This is a static guarantee for SC-006 ("zero submit-for-review transitions can occur without first passing the guard"). If you have ArchUnit on the path, an equivalent rule is preferable; if not, a hand-rolled file walk is acceptable per the user's "cheap-LLM friendly" preference.

- [x] T032 Run the quickstart end-to-end against a fresh local DB. From `sageLine-backend/`: `./mvnw clean spring-boot:run`. In a second terminal, follow `specs/003-workflow-guard/quickstart.md` steps 1–7 verbatim. Confirm: step 1 returns the 14/16 JSON, step 2 returns 422, step 4–5 push STOMP snapshots (use any STOMP CLI like `wscat -c ws://localhost:8089/ws` with a STOMP frame, or write a 20-line Java client), step 6 returns 200, step 7 returns the source-status-mismatch payload. If any step diverges from the documented response, fix the bug and re-run.

- [x] T033 [P] Update `specs/003-workflow-guard/checklists/requirements.md` — append a "Phase complete" line under Notes referencing the SC items proven by each test bundle: SC-001 by US1 bundle, SC-002 by US1 bundle, SC-003 by US2 bundle, SC-004 by US2 bundle, SC-005 by US3 bundle, SC-006 by Polish bundle (T031), SC-007 by US2 bundle, SC-008 by US1 bundle.

- [x] T034 Run `./mvnw clean verify` from `sageLine-backend/`. Must pass with zero failures. Commit.

**Checkpoint**: Phase 003 complete. The single guard funnels every `EN_COURS → EN_REVUE` write; readiness probe + STOMP fanout work; the legacy auto-advance respects the guard without surprising the user.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. Blocks all user stories. T004 → T005 → T006 → T007 strictly sequential (schema → entity → write code → smoke).
- **User Stories (Phase 3+)**: Depend on Foundational. Within each story the test bundle depends on all prior tasks of the same story.
  - US1 is the MVP and should land first.
  - US2 and US3 only depend on US1's foundational additions (`WorkflowReadinessService`, `WorkflowReadinessDTO`, the repo queries) — once US1 lands, US2 and US3 can proceed in parallel by different developers.
- **Polish (Phase 6)**: Depends on US1 (the guard exists) and is best done after US3 so the architecture test in T031 sees the final state.

### User Story Dependencies

- **US1** depends only on Phase 2.
- **US2** depends on Phase 2 + the `WorkflowReadinessService` and `WorkflowReadinessDTO` from US1 (T010, T012, T017). Treat T017 as a hard prerequisite for T023.
- **US3** depends on Phase 2 + `WorkflowReadinessService` from US1 (T017). Otherwise independent.

### Within Each User Story

- DTOs (records) before service classes; service before controller; controller before tests.
- Within Phase 3, T008 / T009 / T010 / T011 / T013 / T014 / T015 / T016 are all `[P]` because they touch separate new files.
- Tests are bundled (T022, T025, T028, plus T030/T031 in polish) — no per-test-class tasks.

### Parallel Opportunities

- T002 ‖ T003 (Setup, different files).
- T008 ‖ T009 ‖ T010 ‖ T011 ‖ T013 ‖ T014 ‖ T015 ‖ T016 (US1 DTOs + workflow primitives, all new files).
- US2 (T023→T024→T025) ‖ US3 (T026→T027→T028) once US1 has merged T010 + T012 + T017 + T019.
- T031 ‖ T033 in polish.

---

## Parallel Example: User Story 1 DTOs + workflow primitives

```bash
# Eight tasks, eight new files, zero shared-state — launch concurrently.
Task: "Create MissingMeasureDTO record at src/main/java/com/pfe/sageline/dtos/response/MissingMeasureDTO.java"
Task: "Create OutOfRangeMeasureDTO record at src/main/java/com/pfe/sageline/dtos/response/OutOfRangeMeasureDTO.java"
Task: "Create WorkflowReadinessDTO record at src/main/java/com/pfe/sageline/dtos/response/WorkflowReadinessDTO.java"
Task: "Create MandatoryCoverageRow record at src/main/java/com/pfe/sageline/dtos/internal/MandatoryCoverageRow.java"
Task: "Create RuleVerdict record at src/main/java/com/pfe/sageline/service/workflow/RuleVerdict.java"
Task: "Create TransitionRule interface at src/main/java/com/pfe/sageline/service/workflow/TransitionRule.java"
Task: "Create SourceStatusRule component at src/main/java/com/pfe/sageline/service/workflow/SourceStatusRule.java"
Task: "Create MandatoryMeasureCoverageRule component at src/main/java/com/pfe/sageline/service/workflow/MandatoryMeasureCoverageRule.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (T001–T003): verify environment.
2. Phase 2 (T004–T007): migration + snapshot column.
3. Phase 3 US1 (T008–T022): guard + 422 envelope + bundled tests.
4. **STOP and VALIDATE** with quickstart steps 1, 2, 6 (skip 3–5 which need US3).
5. Demo if ready.

### Incremental Delivery

1. After MVP: add US2 (T023–T025) → demo the probe.
2. Then US3 (T026–T028) → demo the live STOMP push.
3. Polish (T029–T034) → wire auto-advance, add the static guarantee, run the full quickstart.
4. Each story adds value without breaking the prior ones.

### Parallel Team Strategy

- One developer takes Phase 2 + US1 to MVP.
- Once US1 merges, two developers split US2 and US3 in parallel.
- A third developer starts Polish after both US2 and US3 land.

---

## Notes

- `[P]` tasks = different new files, no shared dependencies.
- `[Story]` label maps task to spec user story.
- Tests are deliberately consolidated into four bundles (T022, T025, T028, T030+T031) per the user's request; do not split them further.
- Verify migrations apply cleanly before each phase by running `./mvnw spring-boot:run` and stopping it.
- Commit after each numbered task or logical group.
- Do **not** introduce ArchUnit, AsyncAPI, or any new dependency without an explicit task — the constitution and the user's brief both push back on dependency churn.
