package com.pfe.sageline.performance;

import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CatalogReadPerformanceIT extends PostgresTestcontainer {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PosteMeasureCatalogRepository repository;

    @BeforeEach
    public void insertTestData() {
        // Insert 1000 test rows
        List<PosteMeasureCatalog> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(PosteMeasureCatalog.builder()
                .posteType(PosteType.WIFI_CONDUIT)
                .measureCode("TEST_CODE_" + String.format("%04d", i))
                .measureLabel("Test Measure " + i)
                .category(MeasureCategory.POWER)
                .defaultUnit("dBm")
                .defaultLowerBound(-40.0)
                .defaultUpperBound(20.0)
                .mandatory(true)
                .displayOrder(i)
                .active(i % 10 != 0) // Make some inactive
                .createdAt(Instant.now())
                .createdBy(1L)
                .updatedAt(Instant.now())
                .updatedBy(1L)
                .build());
        }
        repository.saveAll(rows);
    }

    @Test
    public void readCatalogFor1000Rows_shouldCompleteInUnder200msP95() {
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            long start = System.currentTimeMillis();

            ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/poste-catalog/WIFI_CONDUIT",
                String.class
            );

            long elapsed = System.currentTimeMillis() - start;
            latencies.add(elapsed);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        // Calculate p95
        latencies.sort(null);
        long p95 = latencies.get((int) (latencies.size() * 0.95));

        assertTrue(p95 <= 200, "P95 latency should be <= 200ms, but was " + p95 + "ms");
    }
}
