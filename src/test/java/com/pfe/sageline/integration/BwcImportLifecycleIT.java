package com.pfe.sageline.integration;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.LogFormat;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ImportedLogFileRepository;
import com.pfe.sageline.repository.ValidationMeasureRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T033 — Full BWC import lifecycle against the real supervisor fixture.
 * Covers Story 1 (drag-drop import), Story 2 (preview≡commit), Story 3 (snippet).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BwcImportLifecycleIT extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ImportedLogFileRepository importedLogFileRepository;
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

    private Validation seedWifiConduitTicket() {
        ProductionLine line = seed.seedLine("BWC");
        ValidationZone zone = seed.seedZone("ZONE_BWC_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        return seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
    }

    @Test
    void preview_returns_BWC_format_with_matched_measures() throws Exception {
        Validation ticket = seedWifiConduitTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "bwc-gateway-safran-wifi5g.log", "text/plain", bwcFixture);

        mockMvc.perform(multipart("/api/validations/{id}/preview-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value(LogFormat.BWC.name()))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.matched.length()", greaterThanOrEqualTo(16)));

        // Preview must not persist anything
        assertThat(importedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(ticket.getId())).isEmpty();
        assertThat(measureRepository.findByValidationIdAndMeasureCodeIn(ticket.getId(), List.of("POWER_RMS_AVG_VSA1"))).isEmpty();
    }

    @Test
    void import_persists_measures_and_creates_log_file_row() throws Exception {
        Validation ticket = seedWifiConduitTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "bwc-gateway-safran-wifi5g.log", "text/plain", bwcFixture);

        mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value(LogFormat.BWC.name()))
                .andExpect(jsonPath("$.dryRun").value(false));

        // One ImportedLogFile row created
        assertThat(importedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(ticket.getId()))
                .hasSize(1);

        // Persisted measures all link back to the imported log file row
        List<ValidationMeasure> persisted = measureRepository
                .findByValidationIdAndMeasureCodeIn(ticket.getId(),
                        List.of("POWER_RMS_AVG_VSA1_ANT1_5500", "POWER_RMS_AVG_VSA1_ANT2_5500",
                                "POWER_RMS_AVG_VSA1_ANT3_5500", "POWER_RMS_AVG_VSA1_ANT4_5500"));
        assertThat(persisted).hasSize(4);
        persisted.forEach(m -> {
            assertThat(m.getImportedLogFile()).isNotNull();
            assertThat(m.getSourceLogFile()).isEqualTo("bwc-gateway-safran-wifi5g.log");
        });
    }

    @Test
    void preview_and_commit_produce_equivalent_matched_lists() throws Exception {
        Validation ticket1 = seedWifiConduitTicket();
        Validation ticket2 = seedWifiConduitTicket();

        MockMultipartFile previewFile = new MockMultipartFile(
                "file", "bwc.log", "text/plain", bwcFixture);
        MockMultipartFile importFile = new MockMultipartFile(
                "file", "bwc.log", "text/plain", bwcFixture);

        String previewResponse = mockMvc.perform(multipart("/api/validations/{id}/preview-log", ticket1.getId())
                        .file(previewFile)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String commitResponse = mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket2.getId())
                        .file(importFile)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The matched arrays must contain the same measure codes in the same order
        // (modulo the dryRun flag and ticketId).
        assertThat(extractMatchedCodes(previewResponse))
                .isEqualTo(extractMatchedCodes(commitResponse));
    }

    @Test
    void snippet_endpoint_returns_source_for_imported_measure() throws Exception {
        Validation ticket = seedWifiConduitTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "bwc-gateway-safran-wifi5g.log", "text/plain", bwcFixture);

        mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk());

        ValidationMeasure measure = measureRepository
                .findByValidationIdAndMeasureCodeIn(ticket.getId(), List.of("POWER_RMS_AVG_VSA1_ANT1_5500"))
                .get(0);

        mockMvc.perform(get("/api/validations/{validationId}/measures/{measureId}/source-snippet",
                        ticket.getId(), measure.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.originalFilename").value("bwc-gateway-safran-wifi5g.log"));
    }

    private static java.util.List<String> extractMatchedCodes(String json) {
        // Minimal extraction: find every "measureCode":"..." in the matched array.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"measureCode\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        java.util.List<String> codes = new java.util.ArrayList<>();
        while (m.find()) codes.add(m.group(1));
        // The DTOs that share "measureCode" are MatchedMeasureDTO and UnmatchedMeasureDTO and
        // WouldOverwriteMeasureDTO; for an empty ticket nothing is in wouldOverwrite[] and the
        // matched/unmatched buckets are deterministic, so direct comparison is fine.
        return codes;
    }
}
