package com.pfe.sageline.migration;

import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class LegacyMeasureMigrationIntegrationTest extends PostgresTestcontainer {

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        // Drop and recreate via Flyway baseline to get a clean state each test run
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
    }

    @Test
    void migrationCreatesRowsCorrectlyAndIsIdempotent() {
        // Step 1: Apply up to V1.3 (baseline schema without validation_measures).
        // "classpath:db/test-migration" contains V0.0.1 which creates the core tables
        // (production_lines, validation_zones, validations, validation_results) that are
        // normally created by Hibernate ddl-auto and have no production Flyway migration.
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-migration", "classpath:db/migration")
                .target("1.3")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        // Step 2: Seed prerequisite rows for FK constraints
        jdbc.execute("INSERT INTO production_lines(code,name,active,created_at,updated_at) " +
                "VALUES('MIG_LINE','Migration Line',true,NOW(),NOW())");
        Long lineId = jdbc.queryForObject("SELECT id FROM production_lines WHERE code='MIG_LINE'", Long.class);

        jdbc.execute("INSERT INTO validation_zones(name,poste_type,order_in_line,requires_tool_check,production_line_id,created_at,updated_at) " +
                "VALUES('MIG_ZONE','WIFI_CONDUIT',1,false," + lineId + ",NOW(),NOW())");
        Long zoneId = jdbc.queryForObject("SELECT id FROM validation_zones WHERE name='MIG_ZONE'", Long.class);

        jdbc.execute("INSERT INTO validations(ticket_code,status,priority,validation_zone_id,production_line_id,created_at,updated_at) " +
                "VALUES('MIG-TEST-001','EN_COURS','NORMALE'," + zoneId + "," + lineId + ",NOW(),NOW())");
        Long validationId = jdbc.queryForObject(
                "SELECT id FROM validations WHERE ticket_code='MIG-TEST-001'", Long.class);

        // Step 3: Seed legacy validation_results rows
        jdbc.execute("INSERT INTO validation_results(parameter,measured_value,expected_value,conform,validation_id,created_at) " +
                "VALUES('PWR_2G',5.0,5.0,true," + validationId + ",NOW())");
        jdbc.execute("INSERT INTO validation_results(parameter,measured_value,expected_value,conform,validation_id,created_at) " +
                "VALUES('PWR_5G',12.0,10.0,false," + validationId + ",NOW())");
        jdbc.execute("INSERT INTO validation_results(parameter,measured_value,expected_value,conform,validation_id,created_at) " +
                "VALUES('ZERO_CASE',0.1,0.0,true," + validationId + ",NOW())");

        // Step 4: Run V2.0 - V2.2 migrations
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-migration", "classpath:db/migration")
                .target("2.2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        // Step 5: Assert migrated rows
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM validation_measures WHERE validation_id = ? ORDER BY measure_code", validationId);

        assertThat(rows).hasSize(3);

        Map<String, Object> pwr2g = rows.stream()
                .filter(r -> "PWR_2G".equals(r.get("measure_code"))).findFirst().orElseThrow();
        assertThat((String) pwr2g.get("status")).isEqualTo("OK");
        assertThat(((Number) pwr2g.get("lower_bound")).doubleValue()).isCloseTo(4.75, within(0.01));
        assertThat(((Number) pwr2g.get("upper_bound")).doubleValue()).isCloseTo(5.25, within(0.01));
        assertThat(pwr2g.get("entered_by")).isNull();
        assertThat(pwr2g.get("source_log_file")).isNull();

        Map<String, Object> pwr5g = rows.stream()
                .filter(r -> "PWR_5G".equals(r.get("measure_code"))).findFirst().orElseThrow();
        assertThat((String) pwr5g.get("status")).isEqualTo("OUT_OF_RANGE");
        assertThat(((Number) pwr5g.get("lower_bound")).doubleValue()).isCloseTo(9.5, within(0.01));
        assertThat(((Number) pwr5g.get("upper_bound")).doubleValue()).isCloseTo(10.5, within(0.01));

        Map<String, Object> zeroCase = rows.stream()
                .filter(r -> "ZERO_CASE".equals(r.get("measure_code"))).findFirst().orElseThrow();
        assertThat(((Number) zeroCase.get("lower_bound")).doubleValue()).isCloseTo(-0.5, within(0.01));
        assertThat(((Number) zeroCase.get("upper_bound")).doubleValue()).isCloseTo(0.5, within(0.01));
        assertThat((String) zeroCase.get("status")).isEqualTo("OK");

        // Step 6: Idempotency — re-run migrations, row count must stay at 3
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-migration", "classpath:db/migration")
                .target("2.2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM validation_measures WHERE validation_id = ?", Long.class, validationId);
        assertThat(count).isEqualTo(3);

        // All legacy rows should have migrated_at stamped
        Long migratedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM validation_results WHERE validation_id = ? AND migrated_at IS NOT NULL",
                Long.class, validationId);
        assertThat(migratedCount).isEqualTo(3);
    }
}
