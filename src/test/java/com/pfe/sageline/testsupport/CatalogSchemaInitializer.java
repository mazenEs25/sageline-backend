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
        applyValidationMeasuresConstraints();
        loadSeed();
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = ?)",
            Boolean.class, tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    private void applyPartialUniqueIndex() {
        if (!tableExists("poste_measure_catalog")) return;
        jdbc.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_poste_measure_catalog_active " +
            "ON poste_measure_catalog (poste_type, measure_code) WHERE active = true"
        );
    }

    private void applyCheckConstraint() {
        if (!tableExists("poste_measure_catalog")) return;
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

    private void applyValidationMeasuresConstraints() {
        if (!tableExists("validation_measures")) return;
        jdbc.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_vm_natural_key ON validation_measures(" +
            "validation_id, measure_code, COALESCE(antenna,''), COALESCE(frequency_mhz,-1), COALESCE(modulation_scheme,''))"
        );
        Integer boundsExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_vm_bounds'", Integer.class);
        if (boundsExists != null && boundsExists == 0) {
            jdbc.execute("ALTER TABLE validation_measures ADD CONSTRAINT ck_vm_bounds CHECK (lower_bound < upper_bound)");
        }
        Integer devExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_vm_deviation_consistency'", Integer.class);
        if (devExists != null && devExists == 0) {
            jdbc.execute(
                "ALTER TABLE validation_measures ADD CONSTRAINT ck_vm_deviation_consistency CHECK (" +
                "(measured_value IS NULL AND status = 'NOT_EXECUTED' AND deviation_pct IS NULL) OR " +
                "(measured_value IS NOT NULL AND status IN ('OK','OUT_OF_RANGE') AND deviation_pct IS NOT NULL))");
        }
    }

    private void loadSeed() throws Exception {
        if (!tableExists("poste_measure_catalog")) return;
        try (var stream = new ClassPathResource("db/migration/V1.2__seed_poste_catalog.sql").getInputStream()) {
            String sql = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            jdbc.execute(sql);
        }
    }
}
