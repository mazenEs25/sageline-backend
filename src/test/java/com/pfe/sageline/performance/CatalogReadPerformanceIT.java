package com.pfe.sageline.performance;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class CatalogReadPerformanceIT extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired PosteMeasureCatalogRepository repository;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        List<PosteMeasureCatalog> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(PosteMeasureCatalog.builder()
                .posteType(PosteType.WIFI_CONDUIT)
                .measureCode("PERF_CODE_" + String.format("%04d", i))
                .measureLabel("Test Measure " + i)
                .category(MeasureCategory.POWER)
                .defaultUnit("dBm")
                .defaultLowerBound(-40.0)
                .defaultUpperBound(20.0)
                .mandatory(true)
                .displayOrder(i)
                .active(i % 10 != 0)
                .createdAt(Instant.now())
                .createdBy(1L)
                .updatedAt(Instant.now())
                .updatedBy(1L)
                .build());
        }
        repository.saveAll(rows);
    }

    @Test
    public void readCatalogFor1000Rows_shouldCompleteInUnder200msP95() throws Exception {
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            long start = System.currentTimeMillis();

            mockMvc.perform(get("/api/poste-catalog/WIFI_CONDUIT")
                    .with(jwt().authorities(() -> "ROLE_EXPERT")))
                    .andExpect(status().isOk());

            latencies.add(System.currentTimeMillis() - start);
        }

        latencies.sort(null);
        long p95 = latencies.get((int) (latencies.size() * 0.95));

        assertTrue(p95 <= 200, "P95 latency should be <= 200ms, but was " + p95 + "ms");
    }
}
