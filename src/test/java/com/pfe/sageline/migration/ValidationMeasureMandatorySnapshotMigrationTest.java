package com.pfe.sageline.migration;

import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ValidationMeasureMandatorySnapshotMigrationTest extends PostgresTestcontainer {

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanDb() {
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
    }

    @Test
    void v3_migration_stamps_mandatory_at_creation_correctly() throws Exception {
        // Step 1: Apply up to V2.2 (all migrations before V3.0)
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-migration", "classpath:db/migration")
                .target("2.2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        // Step 2: Seed prerequisite rows
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                "INSERT INTO production_lines(code,name,active,created_at,updated_at) " +
                "VALUES('MGM_LINE','MigMandatory Line',true,NOW(),NOW())");

            ResultSet rsLine = conn.createStatement().executeQuery(
                "SELECT id FROM production_lines WHERE code='MGM_LINE'");
            rsLine.next();
            long lineId = rsLine.getLong(1);

            conn.createStatement().execute(
                "INSERT INTO validation_zones(name,poste_type,order_in_line,requires_tool_check,production_line_id,created_at,updated_at) " +
                "VALUES('MGM_ZONE','WIFI_CONDUIT',1,false," + lineId + ",NOW(),NOW())");

            ResultSet rsZone = conn.createStatement().executeQuery(
                "SELECT id FROM validation_zones WHERE name='MGM_ZONE'");
            rsZone.next();
            long zoneId = rsZone.getLong(1);

            conn.createStatement().execute(
                "INSERT INTO validations(ticket_code,status,priority,validation_zone_id,production_line_id,created_at,updated_at) " +
                "VALUES('MGM-T-001','EN_COURS','NORMALE'," + zoneId + "," + lineId + ",NOW(),NOW())");

            ResultSet rsVal = conn.createStatement().executeQuery(
                "SELECT id FROM validations WHERE ticket_code='MGM-T-001'");
            rsVal.next();
            long validationId = rsVal.getLong(1);

            // Seed a catalog row with mandatory=true
            conn.createStatement().execute(
                "INSERT INTO poste_measure_catalog(poste_type,measure_code,measure_label,category,default_unit," +
                "default_lower_bound,default_upper_bound,mandatory,display_order,active,created_at,updated_at) " +
                "VALUES('WIFI_CONDUIT','MGM_MAND','Mandatory Measure','OTHER','dBm',10.0,15.0,true,1,true,NOW(),NOW())");

            ResultSet rsCat = conn.createStatement().executeQuery(
                "SELECT id FROM poste_measure_catalog WHERE measure_code='MGM_MAND'");
            rsCat.next();
            long catalogId = rsCat.getLong(1);

            // Seed a catalog-linked measure row (mandatory=true template)
            conn.createStatement().execute(
                "INSERT INTO validation_measures(validation_id,catalog_template_id,measure_code,measure_label," +
                "category,unit,lower_bound,upper_bound,measured_value,status,deviation_pct," +
                "measured_at,created_at,updated_at) " +
                "VALUES(" + validationId + "," + catalogId + ",'MGM_MAND','Mandatory Measure'," +
                "'OTHER','dBm',10.0,15.0,NULL,'NOT_EXECUTED',NULL,NOW(),NOW(),NOW())");

            // Seed an ad-hoc measure row (no catalog template)
            conn.createStatement().execute(
                "INSERT INTO validation_measures(validation_id,catalog_template_id,measure_code,measure_label," +
                "category,unit,lower_bound,upper_bound,measured_value,status,deviation_pct," +
                "measured_at,created_at,updated_at) " +
                "VALUES(" + validationId + ",NULL,'MGM_ADHOC','Ad-hoc Measure'," +
                "'OTHER','dBm',10.0,15.0,NULL,'NOT_EXECUTED',NULL,NOW(),NOW(),NOW())");
        }

        // Step 3: Apply V3.0 migration
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/test-migration", "classpath:db/migration")
                .target("3.0")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();

        // Step 4: Assert mandatory_at_creation values
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT measure_code, mandatory_at_creation FROM validation_measures ORDER BY measure_code");

            while (rs.next()) {
                String code = rs.getString("measure_code");
                boolean mandatoryAtCreation = rs.getBoolean("mandatory_at_creation");
                if ("MGM_ADHOC".equals(code)) {
                    assertThat(mandatoryAtCreation).as("ad-hoc measure should have mandatory_at_creation=false").isFalse();
                } else if ("MGM_MAND".equals(code)) {
                    assertThat(mandatoryAtCreation).as("catalog-linked mandatory measure should have mandatory_at_creation=true").isTrue();
                }
            }
        }
    }
}
