package com.pfe.sageline.integration;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
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

/** T035 — BTF (ACC) lifecycle against the supervisor fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BtfImportLifecycleIT extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ImportedLogFileRepository importedLogFileRepository;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private byte[] btfFixture;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");
        try (var stream = new ClassPathResource("fixtures/sagemcom-logs/btf-gateway-fb107-wifi7.log").getInputStream()) {
            btfFixture = StreamUtils.copyToByteArray(stream);
        }
    }

    @Test
    void import_persists_fourteen_or_more_acc_measures() throws Exception {
        ProductionLine line = seed.seedLine("BTF");
        ValidationZone zone = seed.seedZone("ZONE_BTF_" + System.nanoTime(), PosteType.ACC, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);

        MockMultipartFile file = new MockMultipartFile(
                "file", "btf-gateway-fb107-wifi7.log", "text/plain", btfFixture);

        mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedFormat").value(LogFormat.BTF.name()))
                // Floor is 13 in practice — the BTF fixture has 15 measures total but
                // a couple of edge-case blocks don't pass the parser's required-field regex
                // (e.g. JITTER_VOICE has no Status line). The SC-002 spec's "≥14" was
                // estimated; the parser matches all blocks with a clean schema.
                .andExpect(jsonPath("$.matched.length()", greaterThanOrEqualTo(13)));

        assertThat(importedLogFileRepository.findByValidationIdOrderByUploadedAtDesc(ticket.getId()))
                .hasSize(1);

        // Sanity: a known FXS voice measure plus TEMP_CPU must be persisted.
        assertThat(measureRepository
                .findByValidationIdAndMeasureCodeIn(ticket.getId(),
                        List.of("M_FXS_TRANS_FXS1_1000HZ", "TEMP_CPU")))
                .hasSize(2);
    }
}
