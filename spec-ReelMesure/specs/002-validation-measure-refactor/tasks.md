---
description: "Task list for Phase 002 â€” ValidationMeasure Refactor (Backend Only)"
---

# Tasks: ValidationMeasure Refactor (Backend Only)

**Input**: Design documents under `specs/002-validation-measure-refactor/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/validation-measure-api.openapi.yaml`, `quickstart.md`
**Branch**: `002-validation-measure-refactor` (current `master` workspace; Phase 001 already merged)

**Tests**: Included. Constitution gates #2 and #3 make contract and integration tests mandatory for this phase.

## How to read this file (cheap-LLM friendly)

- Every task lists the **exact file path** to create or edit, the **class/method names**, and a **"what to write" outline**. You do not need to invent file paths.
- Tasks are listed in execution order. Do them top-to-bottom unless you see a `[P]` (parallelizable â€” only if working on multiple branches).
- After every task, run `./mvnw clean compile` to confirm nothing broke. Commit after each numbered task or logical group.
- All Java code uses the existing `com.pfe.sageline.*` package layout already established in Phase 001.
- Existing Phase 001 classes you will reuse without modification: `PosteMeasureCatalog` (entity), `PosteMeasureCatalogRepository`, `MeasureCategory` (enum), `MeasureStatus` (enum), `JpaAuditingConfig`, `SecurityUtils`, `GlobalExceptionHandler`, `Validation` (entity), `ValidationRepository`, `User` (entity), `UserRepository`, `TicketStatus` (enum). Do **not** modify any of them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks â‡’ parallelizable.
- **[Story]**: `[US1] â€¦ [US6]`. Setup, Foundational, and Polish phases carry no story label.

## Path Conventions

All paths are relative to the Spring Boot project root: `sageLine-backend/`. New files live under existing packages; no new top-level directories.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the project is in a state where Phase 002 work can begin. There is no new dependency to install â€” Flyway, Testcontainers, JPA auditing, the catalog, and the security stack are all in place from Phase 001.

- [X] T001 Verify pre-conditions: run `./mvnw clean compile` from the repo root â€” it must succeed. Run `./mvnw spring-boot:run` once and confirm Flyway reports applying `V1.0`, `V1.1`, `V1.2`, `V1.3` against the local `sageLine_db`. Stop the app. No code changes in this task; if compile fails, STOP and surface the error to the user before continuing.
- [X] T002 [P] Open `src/main/resources/application.properties`. Confirm these lines exist exactly: `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration`. Do NOT change them.
- [X] T003 [P] Open `pom.xml`. Confirm dependencies present: `flyway-core`, `flyway-database-postgresql`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`, `spring-boot-testcontainers`. Do NOT add or remove anything.

**Checkpoint**: Compile clean, schema at V1.3, no missing dependencies. Phase 002 work can start.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: DDL, the new entity, the repository, the deviation calculator, the editability guard, the exception types, and the controller skeleton. No HTTP behavior wired yet, no data migrated yet.

**âš ï¸ CRITICAL**: No US-labelled task may begin before this phase is complete.

- [X] T004 Create Flyway DDL migration file `src/main/resources/db/migration/V2.0__validation_measure.sql`. Content: a single `CREATE TABLE validation_measures` statement with **exactly** these columns and types (see `data-model.md`):

  ```sql
  CREATE TABLE validation_measures (
      id                   BIGSERIAL PRIMARY KEY,
      validation_id        BIGINT NOT NULL REFERENCES validations(id) ON DELETE CASCADE,
      catalog_template_id  BIGINT NULL REFERENCES poste_measure_catalog(id) ON DELETE SET NULL,
      measure_code         VARCHAR(64) NOT NULL,
      measure_label        VARCHAR(255) NOT NULL,
      category             VARCHAR(32) NOT NULL,
      unit                 VARCHAR(16) NOT NULL,
      lower_bound          DOUBLE PRECISION NOT NULL,
      upper_bound          DOUBLE PRECISION NOT NULL,
      measured_value       DOUBLE PRECISION NULL,
      status               VARCHAR(32) NOT NULL,
      deviation_pct        DOUBLE PRECISION NULL,
      antenna              VARCHAR(16) NULL,
      frequency_mhz        INTEGER NULL,
      modulation_scheme    VARCHAR(32) NULL,
      source_log_file      VARCHAR(255) NULL,
      entered_by           BIGINT NULL REFERENCES users(id),
      measured_at          TIMESTAMP NOT NULL,
      created_at           TIMESTAMP NOT NULL,
      updated_at           TIMESTAMP NOT NULL,
      CONSTRAINT ck_vm_bounds CHECK (lower_bound < upper_bound),
      CONSTRAINT ck_vm_deviation_consistency CHECK (
          (measured_value IS NULL  AND status = 'NOT_EXECUTED'           AND deviation_pct IS NULL) OR
          (measured_value IS NOT NULL AND status IN ('OK','OUT_OF_RANGE') AND deviation_pct IS NOT NULL)
      )
  );
  CREATE INDEX ix_vm_validation ON validation_measures(validation_id);
  CREATE INDEX ix_vm_measure_code ON validation_measures(measure_code);
  CREATE UNIQUE INDEX uq_vm_natural_key ON validation_measures(
      validation_id,
      measure_code,
      COALESCE(antenna, ''),
      COALESCE(frequency_mhz, -1),
      COALESCE(modulation_scheme, '')
  );
  ```

  Do NOT add any other DDL in this file.

- [X] T005 [P] Create JPA entity `src/main/java/com/pfe/sageline/entity/ValidationMeasure.java`. Package `com.pfe.sageline.entity`. Use Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. Annotations: `@Entity`, `@Table(name="validation_measures")`, `@EntityListeners(AuditingEntityListener.class)`. Fields (exact names and types):

  | Field | Type | Annotation |
  |-------|------|------------|
  | `id` | `Long` | `@Id @GeneratedValue(IDENTITY)` |
  | `validation` | `Validation` | `@ManyToOne(fetch=LAZY) @JoinColumn(name="validation_id", nullable=false)` |
  | `catalogTemplate` | `PosteMeasureCatalog` | `@ManyToOne(fetch=LAZY) @JoinColumn(name="catalog_template_id")` (nullable) |
  | `measureCode` | `String` | `@Column(name="measure_code", nullable=false, length=64)` |
  | `measureLabel` | `String` | `@Column(name="measure_label", nullable=false, length=255)` |
  | `category` | `MeasureCategory` | `@Enumerated(STRING) @Column(nullable=false, length=32)` |
  | `unit` | `String` | `@Column(nullable=false, length=16)` |
  | `lowerBound` | `Double` | `@Column(name="lower_bound", nullable=false)` |
  | `upperBound` | `Double` | `@Column(name="upper_bound", nullable=false)` |
  | `measuredValue` | `Double` | `@Column(name="measured_value")` (nullable) |
  | `status` | `MeasureStatus` | `@Enumerated(STRING) @Column(nullable=false, length=32)` |
  | `deviationPct` | `Double` | `@Column(name="deviation_pct")` (nullable) |
  | `antenna` | `String` | `@Column(length=16)` (nullable) |
  | `frequencyMhz` | `Integer` | `@Column(name="frequency_mhz")` (nullable) |
  | `modulationScheme` | `String` | `@Column(name="modulation_scheme", length=32)` (nullable) |
  | `sourceLogFile` | `String` | `@Column(name="source_log_file", length=255)` (nullable) |
  | `enteredBy` | `Long` | `@Column(name="entered_by")` (nullable in DB; set on every write by service) |
  | `measuredAt` | `Instant` | `@Column(name="measured_at", nullable=false)` (set on every write by service) |
  | `createdAt` | `Instant` | `@CreatedDate @Column(name="created_at", nullable=false, updatable=false)` |
  | `updatedAt` | `Instant` | `@LastModifiedDate @Column(name="updated_at", nullable=false)` |

  No `@PrePersist` / `@PreUpdate` methods on the entity (Phase 002 keeps lifecycle behavior in the service layer).

- [X] T006 [P] Create the `MeasureDeviationCalculator` component `src/main/java/com/pfe/sageline/service/MeasureDeviationCalculator.java`. Annotated `@Component`. Pure (no fields, no dependencies). Two public methods:

  ```java
  public MeasureStatus computeStatus(Double measured, double lowerBound, double upperBound);
  public Double computeDeviationPct(Double measured, double lowerBound, double upperBound);
  ```

  Logic per `research.md` R1 and `data-model.md`:
  - If `measured == null` â†’ status = `NOT_EXECUTED`, deviation = `null`.
  - If `lowerBound >= upperBound` â†’ throw `IllegalStateException("halfRange must be > 0")`.
  - Else compute `center = (lower+upper)/2`, `halfRange = (upper-lower)/2`, `deviation = Math.abs(measured-center)/halfRange*100`.
  - status = `OK` when `measured >= lower && measured <= upper`, otherwise `OUT_OF_RANGE`. Boundary values are `OK` (FR-018).

  Add Javadoc that quotes the canonical SC-001 fixtures: `15.5 on [13.5,16.5] â†’ OK, â‰ˆ33.3`; `20.0 on [13.5,16.5] â†’ OUT_OF_RANGE, â‰ˆ433`; `null â†’ NOT_EXECUTED, null`.

- [X] T007 [P] Create `MeasureEditabilityGuard` `src/main/java/com/pfe/sageline/service/MeasureEditabilityGuard.java`. Annotated `@Component`. One public method `public void requireEditable(Validation ticket)`. Behavior: if `ticket.getStatus() != TicketStatus.EN_COURS` â†’ throw `new MeasureNotEditableException(ticket.getId(), ticket.getStatus())`. Otherwise return. (Find the `TicketStatus` enum via `Grep` for `enum TicketStatus`.)

- [X] T008 [P] Create exception `src/main/java/com/pfe/sageline/exception/MeasureNotEditableException.java` extending `RuntimeException`. Constructor `MeasureNotEditableException(Long ticketId, TicketStatus currentStatus)`. Carry both as private final fields with getters. Message: `String.format("Ticket %d is in status %s; measures are read-only outside EN_COURS", ticketId, currentStatus)`.

- [X] T009 [P] Create exception `src/main/java/com/pfe/sageline/exception/BatchMeasureValidationException.java` extending `RuntimeException`. Carry `private final List<FailedEntry> failedEntries` and `private final int totalEntries`. Define `public static class FailedEntry { int index; String code; String message; }` with Lombok `@Data @AllArgsConstructor`. The `code` field is a String, valid values listed in `contracts/validation-measure-api.openapi.yaml` under `BatchMeasureErrorResponse.failedEntries.code.enum`.

- [X] T010 Edit `src/main/java/com/pfe/sageline/exception/GlobalExceptionHandler.java`. Add two `@ExceptionHandler` methods:
  - `handleMeasureNotEditable(MeasureNotEditableException ex)` â†’ HTTP 422 with body `{ "status": 422, "error": "TICKET_NOT_EDITABLE", "message": ex.getMessage(), "ticketId": ex.getTicketId(), "currentStatus": ex.getCurrentStatus() }`.
  - `handleBatchMeasureValidation(BatchMeasureValidationException ex)` â†’ HTTP 422 with body `{ "type": "BATCH_REJECTED", "totalEntries": ex.getTotalEntries(), "failedEntries": ex.getFailedEntries() }`. Also add a handler for `DataIntegrityViolationException` rooted in `uq_vm_natural_key` that returns HTTP 409 with message `"Duplicate measure on this ticket for the given (code, antenna, frequency, modulation)"`. Use exception message matching on `uq_vm_natural_key`; if uncertain, only return 409 when the SQL state is `23505` AND the message contains `uq_vm_natural_key`. Otherwise rethrow.

- [X] T011 [P] Create response DTO `src/main/java/com/pfe/sageline/dtos/response/ValidationMeasureResponse.java` as a Java `record` with these components in this order: `Long id`, `Long validationId`, `Long catalogTemplateId`, `String measureCode`, `String measureLabel`, `MeasureCategory category`, `String unit`, `Double lowerBound`, `Double upperBound`, `Double measuredValue`, `MeasureStatus status`, `Double deviationPct`, `String antenna`, `Integer frequencyMhz`, `String modulationScheme`, `String sourceLogFile`, `Long enteredById`, `String enteredByUsername`, `Instant measuredAt`, `Instant createdAt`, `Instant updatedAt`.

- [X] T012 [P] Create request DTO `src/main/java/com/pfe/sageline/dtos/request/CreateMeasureRequest.java`. Use Lombok `@Data @NoArgsConstructor @AllArgsConstructor`. Fields with Bean Validation:
  - `Long templateId` (optional)
  - `@NotBlank @Size(max=64) @Pattern(regexp="^[A-Z0-9_]+$") String measureCode`
  - `@NotBlank @Size(max=255) String measureLabel`
  - `@NotNull MeasureCategory category`
  - `@NotBlank @Size(max=16) String unit`
  - `@NotNull Double lowerBound`
  - `@NotNull Double upperBound`
  - `Double measuredValue` (nullable)
  - `@Size(max=16) String antenna`
  - `@Min(0) Integer frequencyMhz`
  - `@Size(max=32) String modulationScheme`
  - Add class-level `@AssertTrue(message="lowerBound must be < upperBound") public boolean isBoundsValid() { return lowerBound != null && upperBound != null && lowerBound < upperBound; }`.
  - Note: the `sourceLogFile` field is intentionally absent â€” Phase 002 clients cannot set it (research R8).

- [X] T013 [P] Create request DTO `src/main/java/com/pfe/sageline/dtos/request/UpdateMeasureRequest.java`. All fields optional/nullable: `Double measuredValue`, `Double lowerBound`, `Double upperBound`, `@Size(max=16) String unit`, `@Size(max=16) String antenna`, `@Min(0) Integer frequencyMhz`, `@Size(max=32) String modulationScheme`. Class-level `@AssertTrue(message="when both bounds present, lower < upper")` checks the relation only when both bounds are non-null.

- [X] T014 [P] Create request DTO `src/main/java/com/pfe/sageline/dtos/request/BatchCreateMeasureRequest.java`. One field: `@NotEmpty @Size(max=200) @Valid List<CreateMeasureRequest> measures`. Lombok `@Data`.

- [X] T015 [P] Create repository `src/main/java/com/pfe/sageline/repository/ValidationMeasureRepository.java` extending `JpaRepository<ValidationMeasure, Long>`. Methods:

  ```java
  @Query("""
      SELECT vm FROM ValidationMeasure vm
      LEFT JOIN FETCH vm.catalogTemplate
      WHERE vm.validation.id = :validationId
      ORDER BY vm.measureCode, vm.antenna, vm.frequencyMhz
  """)
  List<ValidationMeasure> findAllByValidationIdFetchTemplate(@Param("validationId") Long validationId);

  Optional<ValidationMeasure> findByIdAndValidationId(Long id, Long validationId);

  boolean existsByValidationIdAndMeasureCodeAndAntennaAndFrequencyMhzAndModulationScheme(
      Long validationId, String measureCode, String antenna, Integer frequencyMhz, String modulationScheme);

  @Query("""
      SELECT vm.measureCode FROM ValidationMeasure vm
      WHERE vm.validation.id = :validationId
        AND vm.catalogTemplate.id IN :templateIds
  """)
  List<Long> findCatalogTemplateIdsPresentOnTicket(@Param("validationId") Long validationId,
                                                   @Param("templateIds") Collection<Long> templateIds);
  ```

  Pick the right return type for the last query: actually return `List<Long>` of the template ids. If easier, declare a small projection. The intent is: "given a ticket and a candidate set of catalog template ids, return the subset already on the ticket." Document the intent in a Javadoc.

- [X] T016 [P] Create mapper `src/main/java/com/pfe/sageline/mappers/ValidationMeasureMapper.java`. `@Component`. One public method: `ValidationMeasureResponse toResponse(ValidationMeasure entity)`. Map all fields. For `enteredById` use `entity.getEnteredBy()`. For `enteredByUsername`: load the user via injected `UserRepository` only if `enteredBy != null`, else `null`. For `catalogTemplateId` use `entity.getCatalogTemplate() != null ? entity.getCatalogTemplate().getId() : null`. Hand-written (no MapStruct).

- [X] T017 Create the `LegacyResultsDeprecationFilter` `src/main/java/com/pfe/sageline/Config/LegacyResultsDeprecationFilter.java`. Extend `OncePerRequestFilter`. Override `doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)`. If `request.getRequestURI().startsWith("/api/validation-results")`, call `response.setHeader("Deprecation", "true")` *before* `filterChain.doFilter(...)`. Annotated `@Component`. Spring picks it up automatically; do NOT register it in `SecurityConfig`.

- [X] T018 Create service interface `src/main/java/com/pfe/sageline/service/ValidationMeasureService.java`. Methods (signatures only):

  ```java
  List<ValidationMeasureResponse> listByValidation(Long validationId);
  ValidationMeasureResponse create(Long validationId, CreateMeasureRequest req);
  List<ValidationMeasureResponse> batchCreate(Long validationId, BatchCreateMeasureRequest req);
  List<ValidationMeasureResponse> instantiateFromCatalog(Long validationId);
  ValidationMeasureResponse update(Long validationId, Long measureId, UpdateMeasureRequest req);
  void delete(Long validationId, Long measureId);
  ```

- [X] T019 Create service implementation skeleton `src/main/java/com/pfe/sageline/service/ValidationMeasureServiceImpl.java`. Annotations: `@Service @Transactional @RequiredArgsConstructor @Slf4j`. Inject: `ValidationMeasureRepository`, `ValidationRepository`, `PosteMeasureCatalogRepository`, `MeasureDeviationCalculator`, `MeasureEditabilityGuard`, `ValidationMeasureMapper`, `SecurityUtils`. Every method throws `UnsupportedOperationException("implemented in US-X")` for now. Methods will be filled per-story.

- [X] T020 Create controller skeleton `src/main/java/com/pfe/sageline/controller/ValidationMeasureController.java`. Annotations: `@RestController @RequestMapping("/api/validations/{validationId}/measures") @RequiredArgsConstructor`. Inject `ValidationMeasureService`. No methods yet â€” added per-story.

- [X] T021 [P] Add base test infrastructure `src/test/java/com/pfe/sageline/testsupport/ValidationMeasureTestSeed.java`. A `@TestComponent` (or plain helper class) that exposes static helpers: `Validation seedTicketInStatus(TicketStatus status, ProductionLine line, ValidationZone zone)`, `PosteMeasureCatalog seedCatalogRow(PosteType posteType, String measureCode, double lower, double upper, String unit, boolean mandatory)`. Used by every integration test in Phases 3+. Reuse the existing `PostgresTestcontainer` base from Phase 001.

- [X] T022 Run `./mvnw clean compile`. Confirm 0 errors. Run `./mvnw spring-boot:run`; Flyway should now log `V2.0 applied`. Hit `GET /api/validations/1/measures` and confirm HTTP 404 *not* 500 (the controller has no handler method; Spring returns "no handler" â†’ 404). Stop the app.

**Checkpoint**: Persistence/DDL/foundation in place; service throws on every call; controller exposes no methods. US1â€“US6 may now proceed.

---

## Phase 3: User Story 1 â€” Record a single industrial measure (Priority: P1) ðŸŽ¯ MVP

**Goal**: Authorized technicians can `POST` a single measured value against a ticket; the server classifies it (`OK`/`OUT_OF_RANGE`/`NOT_EXECUTED`), computes `deviationPct`, stamps `entered_by` + `measured_at`, persists, and returns the full response DTO.

**Independent Test**: Contract test posts the SC-001 fixtures (`15.5 â†’ OK,â‰ˆ33.3`; `20.0 â†’ OUT_OF_RANGE,â‰ˆ433`; null â†’ `NOT_EXECUTED`) against a seeded `EN_COURS` ticket and asserts the response. MVP gate.

### Tests for User Story 1

- [X] T023 [P] [US1] Create `src/test/java/com/pfe/sageline/service/MeasureDeviationCalculatorTest.java`. Pure JUnit 5 (no Spring). Tests:
  - `computesOkAndDeviationForCenterFixture` â†’ input `15.5, 13.5, 16.5` â†’ expect status `OK`, deviation `33.33` (`isCloseTo(33.33, within(0.1))`).
  - `computesOutOfRangeForFixtureTwo` â†’ input `20.0, 13.5, 16.5` â†’ expect status `OUT_OF_RANGE`, deviation `â‰ˆ433.33` (`isCloseTo(433.33, within(0.5))`).
  - `nullMeasuredYieldsNotExecuted` â†’ input `null, 13.5, 16.5` â†’ expect status `NOT_EXECUTED`, deviation `null`.
  - `boundaryValueIsOk` â†’ input `13.5, 13.5, 16.5` â†’ expect status `OK`, deviation `100.0`.
  - `zeroWidthThrows` â†’ input `5.0, 4.0, 4.0` â†’ expect `IllegalStateException`.

- [X] T024 [P] [US1] Create `src/test/java/com/pfe/sageline/controller/ValidationMeasureControllerCreateTest.java` extending `PostgresTestcontainer`. Use `@SpringBootTest` + `MockMvc`. Tests (use `ValidationMeasureTestSeed` from T021):
  - `createMeasure_returnsOk_forInRangeValue` â€” POST as `TECH_VAL` with `measuredValue=15.5, lower=13.5, upper=16.5` on an `EN_COURS` ticket â†’ expect 201, body has `status: "OK"`, `deviationPct` between 33.0 and 33.5.
  - `createMeasure_returnsOutOfRange_forOutOfRangeValue` â€” POST `20.0` with same bounds â†’ 201, `status: "OUT_OF_RANGE"`.
  - `createMeasure_returnsNotExecuted_whenMeasuredIsNull` â€” POST `measuredValue=null` â†’ 201, `status: "NOT_EXECUTED"`, `deviationPct: null`.
  - `createMeasure_rejectsInvertedBounds_400` â€” POST `lower=10, upper=5` â†’ 400.
  - `createMeasure_rejectsSourceLogFile_400` â€” POST a body that includes a `sourceLogFile` field via raw JSON (not a typed DTO) â†’ 400 (the DTO has no such field; Jackson must be configured to reject unknown via `application.properties` `spring.jackson.deserialization.fail-on-unknown-properties=true` â€” if not already set, set it as part of T001 verification; document the fact in the test).

### Implementation for User Story 1

- [X] T025 [US1] In `ValidationMeasureServiceImpl`, implement `create(Long validationId, CreateMeasureRequest req)`:
  1. `Validation ticket = validationRepository.findById(validationId).orElseThrow(() -> new ResourceNotFoundException("Validation", validationId));`
  2. `editabilityGuard.requireEditable(ticket);`
  3. If `req.getTemplateId() != null`: load `PosteMeasureCatalog template = catalogRepository.findById(req.getTemplateId()).orElseThrow(...)`; verify `template.getPosteType() == ticket.getZone().getPosteType()` (else throw `ValidationException`); copy `measureCode/measureLabel/category/unit/lowerBound/upperBound/antenna/frequencyMhz/modulationScheme` from the template, overriding the request's values for those fields. Else copy them from the request directly.
  4. Build the entity (`@Builder`): set all copied fields; `measuredValue` from request; `enteredBy = securityUtils.getCurrentUserId()`; `measuredAt = Instant.now()`; `status = deviationCalculator.computeStatus(...)`; `deviationPct = deviationCalculator.computeDeviationPct(...)`; `validation = ticket`; `catalogTemplate = template-or-null`.
  5. `ValidationMeasure saved = repository.save(entity);`
  6. Return `mapper.toResponse(saved)`.

  Wrap any `DataIntegrityViolationException` and let the global handler map it (do not catch).

- [X] T026 [US1] In `ValidationMeasureController`, add the POST endpoint:

  ```java
  @PostMapping
  @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
  public ResponseEntity<ValidationMeasureResponse> create(
          @PathVariable Long validationId,
          @Valid @RequestBody CreateMeasureRequest req) {
      return ResponseEntity.status(HttpStatus.CREATED).body(service.create(validationId, req));
  }
  ```

- [X] T027 [US1] Verify by running `./mvnw test -Dtest=MeasureDeviationCalculatorTest,ValidationMeasureControllerCreateTest`. All five calculator tests + five controller tests must pass. If `spring.jackson.deserialization.fail-on-unknown-properties` was not set, set it in `application.properties` and re-run.

**Checkpoint**: A single measure can be created and is correctly classified. SC-001 verified. MVP shippable.

---

## Phase 4: User Story 2 â€” Seed a ticket with the full catalog (Priority: P1)

**Goal**: One call instantiates one `NOT_EXECUTED` measure per active catalog template for the ticket's poste type. Idempotent on re-invocation.

**Independent Test**: A ticket on a `WIFI_CONDUIT` zone with 16 active templates â†’ after one call has 16 measures, all `NOT_EXECUTED`, with bounds and unit copied from the templates. Re-invoking creates zero rows (SC-005).

### Tests for User Story 2

- [X] T028 [P] [US2] Create `src/test/java/com/pfe/sageline/controller/ValidationMeasureControllerFromTemplateTest.java`. Tests:
  - `instantiate_createsOneMeasurePerActiveTemplate` â€” seed 16 active `WIFI_CONDUIT` catalog rows + 1 ticket on a `WIFI_CONDUIT` zone in `EN_COURS`. POST `/measures/from-template` â†’ expect 200, response array length 16, every item `status: "NOT_EXECUTED"`, `measuredValue: null`, `deviationPct: null`, `catalogTemplateId` populated.
  - `instantiate_isIdempotent` â€” call twice, second call returns an empty array; DB row count unchanged.
  - `instantiate_returnsEmpty_whenCatalogEmpty` â€” seed a ticket whose poste type has zero catalog rows â†’ response array length 0, HTTP 200.
  - `instantiate_rejectsTicketNotInEnCours_422` â€” set the ticket to `EN_REVUE` â†’ expect 422.
  - `instantiate_skipsInactiveTemplates` â€” set 4 of the 16 templates `active=false` â†’ response array length 12 on first call, all 12 have `catalogTemplateId` matching only the active ones.

### Implementation for User Story 2

- [X] T029 [US2] In `ValidationMeasureServiceImpl`, implement `instantiateFromCatalog(Long validationId)`:
  1. Load and guard the ticket (same as US1 steps 1â€“2).
  2. `PosteType posteType = ticket.getZone().getPosteType();`
  3. `List<PosteMeasureCatalog> templates = catalogRepository.findByPosteTypeAndActiveOrderByDisplayOrder(posteType, true);`
  4. If empty â†’ return `List.of()`.
  5. `List<Long> alreadyPresent = repository.findCatalogTemplateIdsPresentOnTicket(validationId, templates.stream().map(PosteMeasureCatalog::getId).toList());`
  6. For each template not in `alreadyPresent`: build a `ValidationMeasure` with `measuredValue=null`, `status=NOT_EXECUTED`, `deviationPct=null`, all other fields copied from the template; `enteredBy = securityUtils.getCurrentUserId()`; `measuredAt = Instant.now()`; `catalogTemplate = template`; `validation = ticket`.
  7. `repository.saveAll(list)` and return mapped list.

- [X] T030 [US2] In `ValidationMeasureController`, add:

  ```java
  @PostMapping("/from-template")
  @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
  public List<ValidationMeasureResponse> instantiateFromCatalog(@PathVariable Long validationId) {
      return service.instantiateFromCatalog(validationId);
  }
  ```

- [X] T031 [US2] Run `./mvnw test -Dtest=ValidationMeasureControllerFromTemplateTest`. All five tests pass.

**Checkpoint**: A ticket can be seeded in one call. SC-005 verified.

---

## Phase 5: User Story 3 â€” List, update, delete measures (Priority: P2)

**Goal**: Authorized technicians can list every measure on a ticket, update an existing one (status/deviation auto-recompute), and delete one.

**Independent Test**: Create three measures via US1, GET the list and verify all three returned, PUT one with a new `measuredValue`, GET again and verify the row's `status`/`deviationPct` reflect the new value, DELETE the row, GET again and verify it is gone.

### Tests for User Story 3

- [X] T032 [P] [US3] Create `src/test/java/com/pfe/sageline/controller/ValidationMeasureControllerListUpdateDeleteTest.java`. Tests:
  - `list_returnsAllMeasuresForTicket` â€” seed 3 measures via direct repo save (bypass HTTP) and GET; expect length 3, ordered by `measureCode` then `antenna` then `frequencyMhz`.
  - `update_recomputesStatusAndDeviation` â€” start a measure with `measured=15.5/OK`, PUT `measuredValue=20.0` â†’ response `status: OUT_OF_RANGE`, deviation `â‰ˆ433`.
  - `update_nullingMeasuredValue_transitionsToNotExecuted` â€” PUT `measuredValue=null` on an `OK` row â†’ status `NOT_EXECUTED`, deviation `null`.
  - `delete_removesMeasure` â€” DELETE â†’ 204; subsequent GET list does not contain the id.
  - `update_rejectsWhenTicketNotEnCours_422` â€” set ticket to `EN_REVUE`, attempt PUT â†’ 422.

### Implementation for User Story 3

- [X] T033 [US3] In `ValidationMeasureServiceImpl`, implement `listByValidation(Long validationId)`:
  - Verify the ticket exists (`validationRepository.existsById` â†’ else 404 via `ResourceNotFoundException`).
  - `return repository.findAllByValidationIdFetchTemplate(validationId).stream().map(mapper::toResponse).toList();`

- [X] T034 [US3] In `ValidationMeasureServiceImpl`, implement `update(Long validationId, Long measureId, UpdateMeasureRequest req)`:
  1. Load the ticket and call `editabilityGuard.requireEditable(ticket)`.
  2. `ValidationMeasure m = repository.findByIdAndValidationId(measureId, validationId).orElseThrow(() -> new ResourceNotFoundException("ValidationMeasure", measureId));`
  3. For each non-null field on `req`, set it on `m`. Special handling for `measuredValue`: a null in the request payload is **also** the "set to null" signal (Jackson cannot distinguish "field absent" from "field present and null" unless we use `JsonNullable`; for Phase 002, treat null in the JSON body as the explicit clear â€” document the limitation in a Javadoc on the method).
  4. Recompute `status = deviationCalculator.computeStatus(m.getMeasuredValue(), m.getLowerBound(), m.getUpperBound());` and `deviationPct = deviationCalculator.computeDeviationPct(...)`.
  5. Set `enteredBy = securityUtils.getCurrentUserId()` and `measuredAt = Instant.now()` (R4 last-writer-wins audit).
  6. `repository.save(m)`; return mapped response. JPA auditing handles `updatedAt`.

- [X] T035 [US3] In `ValidationMeasureServiceImpl`, implement `delete(Long validationId, Long measureId)`:
  1. Load+guard ticket.
  2. `ValidationMeasure m = repository.findByIdAndValidationId(...).orElseThrow(...)`.
  3. `repository.delete(m);`

- [X] T036 [US3] In `ValidationMeasureController`, add:

  ```java
  @GetMapping
  public List<ValidationMeasureResponse> list(@PathVariable Long validationId) {
      return service.listByValidation(validationId);
  }

  @PutMapping("/{measureId}")
  @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
  public ValidationMeasureResponse update(
          @PathVariable Long validationId,
          @PathVariable Long measureId,
          @Valid @RequestBody UpdateMeasureRequest req) {
      return service.update(validationId, measureId, req);
  }

  @DeleteMapping("/{measureId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
  public void delete(@PathVariable Long validationId, @PathVariable Long measureId) {
      service.delete(validationId, measureId);
  }
  ```

- [X] T037 [US3] Run `./mvnw test -Dtest=ValidationMeasureControllerListUpdateDeleteTest`. All five tests pass.

**Checkpoint**: Full single-resource CRUD ready.

---

## Phase 6: User Story 4 â€” Batch create (transactional, all-or-nothing) (Priority: P2)

**Goal**: One POST creates many measures atomically. Any invalid entry aborts the whole batch with per-entry diagnostics (HTTP 422, body shape per `BatchMeasureErrorResponse`).

**Independent Test**: A batch of 3 valid + 1 invalid (unknown templateId) is rejected with HTTP 422 listing the failing index; the database is unchanged. A batch of 5 valid is created with HTTP 201.

### Tests for User Story 4

- [X] T038 [P] [US4] Create `src/test/java/com/pfe/sageline/controller/ValidationMeasureControllerBatchTest.java`. Tests:
  - `batch_createsAllEntries_onAllValid` â€” 5 valid entries â†’ 201, response length 5, DB has 5 new rows.
  - `batch_rejectsAll_whenOneEntryHasUnknownTemplate` â€” 3 valid + 1 with templateId=999999 â†’ 422, body `type:"BATCH_REJECTED"`, `failedEntries[0].index == 3`, `failedEntries[0].code == "UNKNOWN_TEMPLATE"`, DB row count unchanged.
  - `batch_rejectsAll_whenOneEntryDuplicatesAnExisting` â€” pre-seed an existing measure with code `X`; send a batch where one entry duplicates code `X` â†’ 422, `code: "DUPLICATE_MEASURE_CODE"`. DB unchanged.
  - `batch_rejectsAll_whenTicketNotInEnCours` â€” ticket in `EN_REVUE`, send 3 valid entries â†’ 422, `code: "TICKET_NOT_EDITABLE"` on every failed entry (or a single global entry with `index: -1` â€” pick one shape; document choice in test).
  - `batch_rejectsEmptyMeasuresArray_400` â€” `{"measures": []}` â†’ 400 (bean validation).

### Implementation for User Story 4

- [X] T039 [US4] In `ValidationMeasureServiceImpl`, implement `batchCreate(Long validationId, BatchCreateMeasureRequest req)`:
  1. Load+guard ticket (single check; if not editable, throw `BatchMeasureValidationException` with one global failed entry `index=-1, code="TICKET_NOT_EDITABLE"`).
  2. **Pre-flight pass** (no inserts yet): iterate `req.getMeasures()` with index; for each entry:
     - If `templateId != null` and not found â†’ record `FailedEntry(index, "UNKNOWN_TEMPLATE", "templateId X not found")`.
     - If template exists but its `posteType` differs from ticket zone's â†’ record `FailedEntry(index, "OWNER_MISMATCH", ...)`.
     - Build the prospective natural key (measureCode + antenna + frequencyMhz + modulationScheme, copied from template if applicable). Check both: against the DB via `existsByValidationIdAnd...` AND against the in-batch keys collected so far (a batch with two entries having the same key is itself a duplicate). Record `FailedEntry(index, "DUPLICATE_MEASURE_CODE", ...)`.
  3. If any failed entries â†’ throw `BatchMeasureValidationException(totalEntries, failedEntries)`. `@Transactional` rolls back automatically.
  4. **Apply pass**: build all entities (same logic as `create`), `repository.saveAll(...)`, map and return.

  Important: do not let Jakarta bean validation messages alone catch templateId/owner/duplicate problems â€” those are not field-level constraints; they need the pre-flight pass to produce per-index diagnostics.

- [X] T040 [US4] In `ValidationMeasureController`, add:

  ```java
  @PostMapping("/batch")
  @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
  public ResponseEntity<List<ValidationMeasureResponse>> batchCreate(
          @PathVariable Long validationId,
          @Valid @RequestBody BatchCreateMeasureRequest req) {
      return ResponseEntity.status(HttpStatus.CREATED).body(service.batchCreate(validationId, req));
  }
  ```

- [X] T041 [US4] Run `./mvnw test -Dtest=ValidationMeasureControllerBatchTest`. All five tests pass.

**Checkpoint**: All-or-nothing batch ready. Phase 004's importer will call this endpoint as its commit step.

---

## Phase 7: User Story 5 â€” Ad-hoc measures (no catalog template) (Priority: P2)

**Goal**: Confirm the existing `create` endpoint accepts ad-hoc measures (no `templateId`) and persists them with `catalogTemplate = null`, using the caller-supplied bounds/unit.

**Independent Test**: POST a measure with no `templateId` and explicit code/unit/bounds; the response shows `catalogTemplateId: null` and the supplied values; the row is listed by US3's GET.

### Tests for User Story 5

- [X] T042 [P] [US5] Create `src/test/java/com/pfe/sageline/controller/ValidationMeasureControllerAdHocTest.java`. Tests:
  - `adHoc_persistsWithoutTemplate` â€” POST `{measureCode:"SCRATCH_TEST", measureLabel:"Scratch", category:"OTHER", unit:"V", lowerBound:10, upperBound:14, measuredValue:12}` â†’ 201, `catalogTemplateId: null`, `status: "OK"`, deviation `0.0` (center).
  - `adHoc_subjectToSameDuplicateRule` â€” POST the same payload twice â†’ second call returns 409 (uq_vm_natural_key).
  - `adHoc_rejectsBlankMeasureCode_400` â€” POST with `measureCode: ""` â†’ 400.

### Implementation for User Story 5

- [X] T043 [US5] **No new code.** US1's `create` implementation already handles `templateId == null`. Verify by reading `ValidationMeasureServiceImpl.create` from T025 and confirming the if/else branch covers the null case. If it does not, add the missing `else` branch.
- [X] T044 [US5] Run `./mvnw test -Dtest=ValidationMeasureControllerAdHocTest`. All three tests pass.

**Checkpoint**: Ad-hoc flow verified.

---

## Phase 8: User Story 6 â€” Legacy backward compatibility (Priority: P3)

**Goal**: Existing `/api/validation-results` endpoints continue to respond with HTTP 200 and carry `Deprecation: true`. Legacy rows persist into the new `validation_measures` table via the V2.1 migration, mapped per `data-model.md`.

**Independent Test**: After Flyway runs, query the new endpoint for a ticket that previously had legacy results and verify the migrated rows appear with correct status mapping (`conform=true â†’ OK`, `conform=false â†’ OUT_OF_RANGE`). Hit `/api/validation-results/...` and verify the `Deprecation: true` header is present.

### Tests for User Story 6

- [X] T045 [P] [US6] Create `src/test/java/com/pfe/sageline/legacy/LegacyValidationResultDeprecationHeaderTest.java`. `@SpringBootTest` + `MockMvc`. Test: hit `GET /api/validation-results/validation/{id}` (the existing legacy endpoint â€” find its exact path via `Grep` in `ValidationResultController`) and assert the response header `Deprecation` equals `"true"`. Hit a second legacy endpoint (any one) and assert the same. Hit `/api/validations/{id}/measures` (the new endpoint) and assert the `Deprecation` header is **absent**.

- [X] T046 [P] [US6] Create `src/test/java/com/pfe/sageline/migration/LegacyMeasureMigrationIntegrationTest.java` extending `PostgresTestcontainer`. The test:
  1. Disables auto-Flyway in the Spring context (`@TestPropertySource(properties = "spring.flyway.enabled=false")`).
  2. Manually runs V1.0â€“V1.3 via `Flyway.configure().target("1.3").migrate()`.
  3. Seeds legacy rows directly via `JdbcTemplate`: insert a `validations` row, then insert 3 `validation_results` rows: `(parameter='PWR_2G', measured_value=5.0, expected_value=5.0, conform=true)`, `(parameter='PWR_5G', measured_value=12.0, expected_value=10.0, conform=false)`, `(parameter='ZERO_CASE', measured_value=0.1, expected_value=0.0, conform=true)`.
  4. Runs `Flyway.configure().target("2.2").migrate()` to apply V2.0â€“V2.2.
  5. Queries `validation_measures` for the ticket and asserts:
     - 3 rows present.
     - Row for `PWR_2G`: `measure_code='PWR_2G'`, `status='OK'`, `lower_bound â‰ˆ 4.75`, `upper_bound â‰ˆ 5.25`, `entered_by IS NULL`, `source_log_file IS NULL`.
     - Row for `PWR_5G`: `status='OUT_OF_RANGE'`, `lower_bound â‰ˆ 9.5`, `upper_bound â‰ˆ 10.5`.
     - Row for `ZERO_CASE`: `lower_bound = -0.5`, `upper_bound = 0.5`, `status='OK'`.
  6. Re-runs `Flyway.configure().target("2.2").migrate()` (idempotency): asserts `validation_measures` count is still 3 (no duplicates) and that `validation_results.migrated_at` is non-null on all three rows.

### Implementation for User Story 6

- [X] T047 [US6] Create `src/main/resources/db/migration/V2.1__validation_measure_data_migration.sql`. Body (only SQL; no procedural blocks unless necessary):

  ```sql
  INSERT INTO validation_measures (
      validation_id, catalog_template_id, measure_code, measure_label, category,
      unit, lower_bound, upper_bound, measured_value, status, deviation_pct,
      antenna, frequency_mhz, modulation_scheme, source_log_file,
      entered_by, measured_at, created_at, updated_at
  )
  SELECT
      vr.validation_id,
      NULL,
      UPPER(REGEXP_REPLACE(vr.parameter, '[^A-Za-z0-9]', '_', 'g')),
      vr.parameter,
      'OTHER',
      'unknown',
      CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END,
      CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END,
      vr.measured_value,
      CASE WHEN vr.conform THEN 'OK' ELSE 'OUT_OF_RANGE' END,
      -- deviationPct must be consistent with status & measured (CHECK constraint).
      -- compute against the synthesized window; halfRange > 0 always.
      CASE
        WHEN vr.measured_value IS NULL THEN NULL
        ELSE ABS(
                 vr.measured_value
                 - ((CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END)
                  + (CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END)) / 2
             ) /
             ((CASE WHEN vr.expected_value = 0 THEN  0.5 ELSE vr.expected_value * 1.05 END)
            - (CASE WHEN vr.expected_value = 0 THEN -0.5 ELSE vr.expected_value * 0.95 END)) * 2 * 100
      END,
      NULL, NULL, NULL, NULL,
      NULL,
      COALESCE(vr.created_at, NOW()),
      COALESCE(vr.created_at, NOW()),
      NOW()
  FROM validation_results vr
  WHERE vr.migrated_at IS NULL;
  ```

  Note: V2.1 alone does not stamp `migrated_at` â€” that happens in V2.2 (so the column must exist before stamping). For ordering, see T048.

- [X] T048 [US6] Create `src/main/resources/db/migration/V2.2__validation_results_legacy_marker.sql`:

  ```sql
  ALTER TABLE validation_results ADD COLUMN IF NOT EXISTS migrated_at TIMESTAMP NULL;

  UPDATE validation_results vr
  SET    migrated_at = NOW()
  WHERE  migrated_at IS NULL
    AND  EXISTS (
           SELECT 1 FROM validation_measures vm
           WHERE  vm.validation_id = vr.validation_id
             AND  vm.measure_code   = UPPER(REGEXP_REPLACE(vr.parameter, '[^A-Za-z0-9]', '_', 'g'))
         );
  ```

  Note ordering: V2.0 creates `validation_measures`; V2.1 inserts the rows; V2.2 adds the column and stamps. On subsequent runs, V2.1's `WHERE migrated_at IS NULL` is the idempotency gate (the column already exists from the prior V2.2 run). **Caveat**: on the very first run, V2.1 executes before the `migrated_at` column exists, so `WHERE vr.migrated_at IS NULL` would fail. Fix by reordering: rename V2.1 to `V2.2`, V2.2 to `V2.1` so the column is added first. Apply this reorder during this task. Update T047's filename accordingly. **Verify final filenames:** `V2.0__validation_measure.sql`, `V2.1__validation_results_legacy_marker.sql` (adds column), `V2.2__validation_measure_data_migration.sql` (inserts + relies on column).

- [X] T049 [US6] Run `./mvnw test -Dtest=LegacyValidationResultDeprecationHeaderTest,LegacyMeasureMigrationIntegrationTest`. Both pass. If the migration test's idempotency assertion fails (duplicate rows after second run), the `WHERE vr.migrated_at IS NULL` filter is missing or column ordering is wrong â€” re-check T048.

**Checkpoint**: Backward-compat satisfied per Constitution VIII (header present, legacy rows visible through new endpoint, migration idempotent).

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T050 [P] Add an end-to-end quickstart verification script: open `quickstart.md` and run every PowerShell snippet against a fresh local DB. If any step fails, fix the corresponding code/test; do not edit the quickstart to paper over a bug.
- [X] T051 [P] Run the full test suite: `./mvnw test`. Expect 0 failures; if any pre-existing Phase 001 test breaks, surface it to the user â€” do not edit Phase 001 code to make Phase 002 tests pass.
- [X] T052 Run a final `./mvnw clean package -DskipTests=false`. The build must produce `target/sageLine-backend-*.jar` and exit 0.
- [X] T053 Update the project root `CLAUDE.md` (the backend one at `sageLine-backend/CLAUDE.md`, NOT the spec-ReelMesure one) by appending one line under "Key Services": `- ValidationMeasureService â€” manages bounded-tolerance industrial measures per ticket; auto-classifies via MeasureDeviationCalculator; gated to EN_COURS by MeasureEditabilityGuard (Phase 002)`.
- [X] T054 Smoke-test the legacy deprecation: with the app running, `curl -i http://localhost:8089/api/validation-results/...` (use any existing path) â€” confirm `Deprecation: true` header is in the response.
- [X] T055 Final verification: tick this box only when every prior task is `[x]` and every test in `./mvnw test` passes.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: independent â€” first thing to do.
- **Foundational (Phase 2)**: depends on Setup. Blocks all US-labelled work.
- **US1 (Phase 3)**: depends on Phase 2.
- **US2 (Phase 4)**: depends on Phase 2; benefits from US1's `create` logic existing but does not strictly require US1 endpoints.
- **US3 (Phase 5)**: depends on Phase 2; benefits from at least one row in `validation_measures` (which the test seeds via repository, so no hard dep on US1's HTTP path).
- **US4 (Phase 6)**: depends on Phase 2; reuses the same service-level `create` logic introduced by US1 â€” implement US1 first to avoid duplication.
- **US5 (Phase 7)**: piggy-backs entirely on US1's `create`; do US1 first.
- **US6 (Phase 8)**: depends on Phase 2 (the new table must exist before V2.1 inserts into it). The deprecation-header test depends only on T017.
- **Polish (Phase 9)**: last.

### Recommended execution order (linear, single-developer)

T001 â†’ T002 â†’ T003 (Setup) â†’ T004 â†’ T005 â†’ â€¦ â†’ T022 (Foundational) â†’ T023 â†’ T024 â†’ T025 â†’ T026 â†’ T027 (US1, MVP) â†’ T028 â†’ T029 â†’ T030 â†’ T031 (US2) â†’ T032 â†’ T033 â†’ T034 â†’ T035 â†’ T036 â†’ T037 (US3) â†’ T038 â†’ T039 â†’ T040 â†’ T041 (US4) â†’ T042 â†’ T043 â†’ T044 (US5) â†’ T045 â†’ T046 â†’ T047 â†’ T048 â†’ T049 (US6) â†’ T050 â†’ T051 â†’ T052 â†’ T053 â†’ T054 â†’ T055 (Polish).

### Parallel opportunities

- T002, T003 in parallel (Setup).
- T005, T006, T007, T008, T009, T011, T012, T013, T014, T015, T016 all in parallel after T004 (Foundational): different files.
- T023, T024 in parallel (US1 tests; different files).
- The five `â€¦ControllerXxxTest.java` test files (T024, T028, T032, T038, T042) are pairwise independent â€” write any of them before its implementation if practicing TDD; they are all marked `[P]`.

---

## Implementation Strategy

### MVP first (US1 only)

1. Setup (T001â€“T003).
2. Foundational (T004â€“T022).
3. US1 (T023â€“T027): single-measure record + classify. SC-001 verified. **Stop and demo.**

### Incremental delivery

After the MVP, ship one US at a time. Each phase's checkpoint is the ship gate.

1. MVP (US1).
2. + US2 (catalog seeding) â€” Phase 004's log importer will need it.
3. + US3 (CRUD list/update/delete) â€” the frontend (Phase 002's Angular sub-project, deferred) will need it.
4. + US4 (batch) â€” the log importer's commit step.
5. + US5 (ad-hoc) â€” small, mostly free.
6. + US6 (legacy backward-compat) â€” close the Constitution VIII obligation.
7. Polish.

---

## Notes for the implementing model

- Every task's "what to write" outline contains the **exact method signature and field name** you should use. Do not rename them.
- If a `Grep` for a referenced existing symbol (e.g. `TicketStatus`, `SecurityUtils.getCurrentUserId`, `ResourceNotFoundException`, `ValidationException`, `ProductionLine`, `ValidationZone`, `PosteType`) returns no result, **stop and ask the user before improvising**. These are all Phase 001 (or earlier) artifacts that should be present.
- Do NOT modify any file under `src/main/java/com/pfe/sageline/entity/PosteMeasureCatalog.java` or any other Phase 001 source. The only existing files this phase edits are `application.properties` (T001/T024 jackson flag), `GlobalExceptionHandler.java` (T010), and the project root `CLAUDE.md` (T053).
- Do NOT add a `@Version` column to `ValidationMeasure`. Concurrency is last-writer-wins (research R4).
- Do NOT push WebSocket messages from this phase's service. STOMP push of readiness is Phase 003.
- Do NOT parse `.log` files in this phase. Log parsing is Phase 004.
- All tests must run against a Testcontainers Postgres (reuse `PostgresTestcontainer` from Phase 001). Do not switch to H2 â€” the migrations use Postgres-specific functions (`REGEXP_REPLACE`, `COALESCE` in unique index) that H2 will reject.
- Commit after every numbered task or every group of `[P]` tasks. Use commit messages of the form `[002][T0NN] <one-line summary>`.
- If you find that a task's recipe is wrong (e.g., a referenced repository method does not match what `ValidationMeasureRepository` actually needs), update the task description **first**, then implement.

