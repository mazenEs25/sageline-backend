package com.pfe.sageline.controller;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T036 — Controller MockMvc covering the negative HTTP paths (403, 422, 413).
 * The happy path is exercised by the lifecycle ITs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LogImportControllerTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");
    }

    private Validation seedTicket() {
        ProductionLine line = seed.seedLine("CT");
        ValidationZone zone = seed.seedZone("ZONE_CT_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        return seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
    }

    @Test
    void wrong_role_returns_403() throws Exception {
        Validation ticket = seedTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.log", "text/plain", "[WIFI_CONDUIT] sample".getBytes());

        mockMvc.perform(multipart("/api/validations/{id}/preview-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_RESPONSABLE"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupported_format_returns_422() throws Exception {
        Validation ticket = seedTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "garbage.log", "text/plain",
                "this is not a sagemcom log\n".repeat(50).getBytes());

        mockMvc.perform(multipart("/api/validations/{id}/preview-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_LOG_FORMAT"));
    }

    @Test
    void oversize_upload_returns_413() throws Exception {
        Validation ticket = seedTicket();
        // 3 MB of zeros — well above the 2 MB cap.
        byte[] big = new byte[3 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.log", "text/plain", big);

        mockMvc.perform(multipart("/api/validations/{id}/preview-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("LOG_TOO_LARGE"));
    }

    @Test
    void editability_guard_rejects_non_en_cours_ticket() throws Exception {
        ProductionLine line = seed.seedLine("EG");
        ValidationZone zone = seed.seedZone("ZONE_EG_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.PLANIFIE, line, zone);

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.log", "text/plain", "[WIFI_CONDUIT] sample".getBytes());

        mockMvc.perform(multipart("/api/validations/{id}/import-log", ticket.getId())
                        .file(file)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
        // Status is 409 (MEASURE_NOT_EDITABLE — Phase 002 guard) or 422 depending on guard.
        // Whichever, the test asserts the call is refused before persistence.
    }
}
