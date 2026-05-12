package com.pfe.sageline.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Postgres Testcontainer base. Uses the singleton-container pattern:
 * the container is started once via a static initializer when this class is
 * first loaded, and lives until the JVM exits (no per-class stop/restart).
 *
 * <p>Deliberately does NOT carry {@code @SpringBootTest} so subclasses can
 * choose their own slice annotation. The {@code @DynamicPropertySource} wires
 * the datasource URL for every Spring context that inherits from this class.
 */
@ActiveProfiles("test")
public abstract class PostgresTestcontainer {

    public static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("test_sageline_db")
                .withUsername("postgres")
                .withPassword("postgres")
                .withReuse(true);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
