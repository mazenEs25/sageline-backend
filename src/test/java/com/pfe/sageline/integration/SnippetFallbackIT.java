package com.pfe.sageline.integration;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.LogImportOptionsDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ImportedLogFileRepository;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.service.Import.LogImportService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T040 — Snippet endpoint returns 200 with available=false when the on-disk log
 * has been deleted (FR-009 disaster-recovery fallback).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SnippetFallbackIT extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired LogImportService logImportService;
    @Autowired ImportedLogFileRepository importedLogFileRepository;
    @Autowired ValidationMeasureRepository measureRepository;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private byte[] bwcFixture;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");

        try (var stream = new ClassPathResource("fixtures/sagemcom-logs/bwc-gateway-safran-wifi5g.log").getInputStream()) {
            bwcFixture = StreamUtils.copyToByteArray(stream);
        }
    }

    @Test
    void deleted_on_disk_log_yields_available_false() throws Exception {
        ProductionLine line = seed.seedLine("SF");
        ValidationZone zone = seed.seedZone("ZONE_SF_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);

        MockMultipartFile file = new MockMultipartFile(
                "file", "bwc-gateway-safran-wifi5g.log", "text/plain", bwcFixture);
        logImportService.importLog(ticket.getId(), file, new LogImportOptionsDTO(false), null);

        // Grab a persisted measure and its source-file path. Use the ImportedLogFileRepository
        // directly so we don't trigger the @ManyToOne lazy proxy on ValidationMeasure outside
        // of a transaction.
        ValidationMeasure measure = measureRepository
                .findByValidationIdAndMeasureCodeIn(ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500"))
                .get(0);
        Path stored = Path.of(importedLogFileRepository
                .findByValidationIdOrderByUploadedAtDesc(ticket.getId())
                .get(0)
                .getStoredPath());

        // Simulate disaster recovery: delete the on-disk file but keep the row.
        Files.deleteIfExists(stored);

        mockMvc.perform(get("/api/validations/{validationId}/measures/{measureId}/source-snippet",
                        ticket.getId(), measure.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.snippet").doesNotExist());
    }
}
