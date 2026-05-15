package com.pfe.sageline.migration;

import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T032 — Verifies the Phase 004 schema additions and seeded fixture aliases.
 *
 * <p>Flyway is disabled in the {@code test} profile (Hibernate owns the schema),
 * so this test asserts the entity-derived schema PLUS the V4.2 seed rows that
 * {@code CatalogSchemaInitializer} applies programmatically.
 */
@SpringBootTest
class ImportedLogFileMigrationTest extends PostgresTestcontainer {

    @Autowired JdbcTemplate jdbc;

    @Test
    void imported_log_file_table_exists() {
        Boolean exists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'imported_log_file')",
            Boolean.class);
        assertThat(exists).isTrue();
    }

    @Test
    void measure_code_alias_table_exists_with_unique_constraint() {
        Boolean tableExists = jdbc.queryForObject(
            "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'measure_code_alias')",
            Boolean.class);
        assertThat(tableExists).isTrue();

        Integer constraintCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uk_measure_code_alias_poste_source'",
            Integer.class);
        assertThat(constraintCount).isEqualTo(1);
    }

    @Test
    void validation_measures_carries_imported_log_file_id_and_source_declared_status() {
        Integer importedLogColCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name = 'validation_measures' AND column_name = 'imported_log_file_id'",
            Integer.class);
        assertThat(importedLogColCount).isEqualTo(1);

        Integer sourceStatusColCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name = 'validation_measures' AND column_name = 'source_declared_status'",
            Integer.class);
        assertThat(sourceStatusColCount).isEqualTo(1);
    }

    @Test
    void temps_test_alias_seeded_for_bnft_fixture() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM measure_code_alias " +
            "WHERE poste_type = 'TEST_FONCTIONNEL' AND source_code = 'Temps_Test' AND catalog_measure_code = 'TEMPS_TEST'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void bwc_power_rms_aliases_seeded() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM measure_code_alias " +
            "WHERE poste_type = 'WIFI_CONDUIT' AND source_code LIKE 'POWER_RMS_AVG_VSA%'",
            Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void bwc_evm_catalog_rows_seeded() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM poste_measure_catalog " +
            "WHERE poste_type = 'WIFI_CONDUIT' AND measure_code LIKE 'EVM_%' AND active = true",
            Integer.class);
        assertThat(count).isEqualTo(8);
    }
}
