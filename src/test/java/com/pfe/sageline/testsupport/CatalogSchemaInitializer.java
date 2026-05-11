package com.pfe.sageline.testsupport;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * Bootstrap helper invoked once per Spring context in tests: drops & recreates
 * the partial unique index that Hibernate cannot express on its own, then loads
 * the V1.2 seed data so integration tests see a populated reference catalog.
 *
 * <p>Active only when the {@code test} profile is on (see
 * {@code @ActiveProfiles("test")} on {@link PostgresTestcontainer}). Repository
 * and controller tests that do not need seed data can opt out by overriding the
 * bean or by truncating in a test fixture.
 */
@Component
public class CatalogSchemaInitializer {

    private final JdbcTemplate jdbc;

    public CatalogSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() throws Exception {
        applyPartialUniqueIndex();
        applyCheckConstraint();
        loadSeed();
    }

    private void applyPartialUniqueIndex() {
        jdbc.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_poste_measure_catalog_active " +
            "ON poste_measure_catalog (poste_type, measure_code) WHERE active = true"
        );
    }

    private void applyCheckConstraint() {
        // Hibernate doesn't emit the bounds CHECK from the entity, so we add it here.
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'chk_poste_measure_catalog_bounds'",
            Integer.class
        );
        if (exists != null && exists == 0) {
            jdbc.execute(
                "ALTER TABLE poste_measure_catalog " +
                "ADD CONSTRAINT chk_poste_measure_catalog_bounds " +
                "CHECK (default_lower_bound < default_upper_bound)"
            );
        }
    }

    private void loadSeed() throws Exception {
        try (var stream = new ClassPathResource("db/migration/V1.2__seed_poste_catalog.sql").getInputStream()) {
            String sql = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            // The seed file is idempotent: ON CONFLICT for active rows, WHERE NOT EXISTS for inactive.
            jdbc.execute(sql);
        }
    }
}
