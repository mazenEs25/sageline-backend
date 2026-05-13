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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WorkflowReadinessControllerTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private Long ticketId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");

        ProductionLine line = seed.seedLine("WRC");
        ValidationZone zone = seed.seedZone("ZONE_WRC_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        ticketId = ticket.getId();
    }

    @Test
    void returns_200_with_all_10_top_level_fields() throws Exception {
        mockMvc.perform(get("/api/validations/{id}/readiness", ticketId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").exists())
                .andExpect(jsonPath("$.currentStatus").exists())
                .andExpect(jsonPath("$.targetStatus").exists())
                .andExpect(jsonPath("$.mandatoryTotal").exists())
                .andExpect(jsonPath("$.mandatoryFilled").exists())
                .andExpect(jsonPath("$.mandatoryMissing").exists())
                .andExpect(jsonPath("$.missingMeasures").exists())
                .andExpect(jsonPath("$.outOfRangeMeasures").exists())
                .andExpect(jsonPath("$.canTransition").exists())
                .andExpect(jsonPath("$.blockingReasons").exists());
    }

    @Test
    void returns_200_with_explicit_targetStatus_param() throws Exception {
        mockMvc.perform(get("/api/validations/{id}/readiness?targetStatus=EN_REVUE", ticketId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetStatus").value("EN_REVUE"));
    }

    @Test
    void returns_404_for_missing_ticket() throws Exception {
        mockMvc.perform(get("/api/validations/{id}/readiness", 9999999L)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/validations/{id}/readiness", ticketId))
                .andExpect(status().isUnauthorized());
    }
}
