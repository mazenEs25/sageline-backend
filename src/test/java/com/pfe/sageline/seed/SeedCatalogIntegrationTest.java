package com.pfe.sageline.seed;

import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the V1.2 seed (loaded by CatalogSchemaInitializer in the test
 * profile) populates the reference catalog with the counts the spec demands
 * (SC-001) and that re-running the same seed leaves the database unchanged (SC-005).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeedCatalogIntegrationTest extends PostgresTestcontainer {

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired private PosteMeasureCatalogRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void seedProvidesExpectedCountsForThreePostes() {
        List<PosteMeasureCatalog> testFonctionnel = repository
            .findByPosteTypeAndActiveOrderByDisplayOrder(PosteType.TEST_FONCTIONNEL, true);
        assertTrue(testFonctionnel.size() >= 6,
            "TEST_FONCTIONNEL should have at least 6 active measures, got " + testFonctionnel.size());

        List<PosteMeasureCatalog> wifiConduit = repository
            .findByPosteTypeAndActiveOrderByDisplayOrder(PosteType.WIFI_CONDUIT, true);
        assertTrue(wifiConduit.size() >= 16,
            "WIFI_CONDUIT should have at least 16 active measures, got " + wifiConduit.size());

        List<PosteMeasureCatalog> acc = repository
            .findByPosteTypeAndActiveOrderByDisplayOrder(PosteType.ACC, true);
        assertTrue(acc.size() >= 14,
            "ACC should have at least 14 active measures, got " + acc.size());

        // Sort assertion across all three lists.
        for (List<PosteMeasureCatalog> list : List.of(testFonctionnel, wifiConduit, acc)) {
            for (int i = 1; i < list.size(); i++) {
                assertTrue(list.get(i - 1).getDisplayOrder() <= list.get(i).getDisplayOrder(),
                    "Templates should be ordered by displayOrder ascending");
            }
        }
    }

    @Test
    void seedExposesExpectedMeasureCodes() {
        assertTrue(repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(
            PosteType.TEST_FONCTIONNEL, "MES_BNFT_PWR0_2G"));
        assertTrue(repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(
            PosteType.WIFI_CONDUIT, "POWER_RMS_AVG_VSA1_ANT1_5250"));
        assertTrue(repository.existsByPosteTypeAndMeasureCodeAndActiveTrue(
            PosteType.ACC, "M_FXS_TRANS_FXS1_1000HZ"));

        // Every active row must satisfy the bounds invariant (defense-in-depth on the seed).
        for (PosteMeasureCatalog m : repository.findByActive(true)) {
            assertNotNull(m.getMandatory(), "mandatory must not be null");
            assertNotNull(m.getDefaultLowerBound(), "defaultLowerBound must not be null");
            assertNotNull(m.getDefaultUpperBound(), "defaultUpperBound must not be null");
            assertTrue(m.getDefaultLowerBound() < m.getDefaultUpperBound(),
                "Lower bound must be strictly less than upper bound for " + m.getMeasureCode());
        }
    }

    /**
     * True idempotency check: re-execute the V1.2 seed SQL directly against the
     * live test database and verify the row count is unchanged. This catches the
     * case where ON CONFLICT / WHERE NOT EXISTS guards regress.
     */
    @Test
    void seedIsTrulyIdempotentOnReExecution() throws Exception {
        long beforeActive = repository.count();
        long beforeRows = jdbc.queryForObject("SELECT COUNT(*) FROM poste_measure_catalog", Long.class);

        try (var stream = new ClassPathResource("db/migration/V1.2__seed_poste_catalog.sql").getInputStream()) {
            String sql = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            jdbc.execute(sql);
        }

        long afterActive = repository.count();
        long afterRows = jdbc.queryForObject("SELECT COUNT(*) FROM poste_measure_catalog", Long.class);

        assertEquals(beforeActive, afterActive, "Active row count must not change on seed re-run");
        assertEquals(beforeRows, afterRows, "Total row count (including inactive) must not change on seed re-run");
    }
}
