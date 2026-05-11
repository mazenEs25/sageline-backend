package com.pfe.sageline.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Postgres Testcontainer base. Deliberately does NOT carry
 * {@code @SpringBootTest} or {@code @DataJpaTest} so subclasses can choose
 * their own slice annotation (e.g. {@code @DataJpaTest} for repository tests,
 * {@code @SpringBootTest(webEnvironment = NONE)} for full-context tests,
 * {@code @WebMvcTest} for controller-only tests with mocked services).
 *
 * <p>The container is started once per JVM and reused across test classes.
 * Hibernate creates tables from the entities (see
 * {@code application-test.properties}); the catalog-specific deltas (partial
 * unique index + reference seed) are applied by {@link CatalogSchemaInitializer}.
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class PostgresTestcontainer {

    @Container
    public static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_sageline_db")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
