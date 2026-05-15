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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T034 — BNFT (TEST_FONCTIONNEL) lifecycle against the supervisor fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BnftImportLifecycleIT extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ImportedLogFileRepository importedLogFileRepository;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private byte[] bnftFixture;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");
        try (var stream = new ClassPathResource("fixtures/sagemcom-logs/bnft-decoder-M393.txt").getInputStream()) {
            bnftFixture = StreamUtils.copyToByteArray(stream);
        }
    }

    @Test
    void import_persists_six_bnft_measures() throws Exception {
        ProductionLine line = seed.seedLine("BNFT");
        ValidationZone zone = seed.seedZone("ZONE_BNFT_" + System.nanoTime(), PosteType.TEST_FONCTIONNEL, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);

        MockMultipartFile file = new MockMultipartFile(
                "file", "bnft-decoder-M393.txt", "text/plain", bnftFixture);

        mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value(LogFormat.BNFT.name()))
                .andExpect(jsonPath("$.matched.length()", greaterThanOrEqualTo(6)));

        assertThat(importedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(ticket.getId()))
                .hasSize(1);

        // All five BNFT power measures plus TEMPS_TEST (aliased from Temps_Test) — 6 total.
        List<ValidationMeasure> persisted = measureRepository
                .findByValidationIdAndMeasureCodeIn(ticket.getId(),
                        List.of("MES_BNFT_PWR0_2G", "MES_BNFT_PWR1_2G", "MES_BNFT_PWR0_5G",
                                "MES_BNFT_PWR1_5G", "MES_BNFT_PWR0_BT", "TEMPS_TEST"));
        assertThat(persisted).hasSize(6);
        persisted.forEach(m -> {
            assertThat(m.getMeasuredValue()).isNotNull();
            assertThat(m.getImportedLogFile()).isNotNull();
        });
    }
}
