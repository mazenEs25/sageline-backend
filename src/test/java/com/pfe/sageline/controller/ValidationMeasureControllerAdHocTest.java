package com.pfe.sageline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ValidationMeasureControllerAdHocTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired ValidationMeasureTestSeed seed;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;
    private Long validationId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        ProductionLine line = seed.seedLine("ADH");
        ValidationZone zone = seed.seedZone("ZONE_ADH_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        validationId = ticket.getId();
    }

    @Test
    void adHoc_persistsWithoutTemplate() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "SCRATCH_TEST",
                "measureLabel", "Scratch",
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 10.0,
                "upperBound", 14.0,
                "measuredValue", 12.0
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.catalogTemplateId").doesNotExist())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void adHoc_subjectToSameDuplicateRule() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "SCRATCH_DUP",
                "measureLabel", "Scratch dup",
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 10.0,
                "upperBound", 14.0,
                "measuredValue", 12.0
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void adHoc_rejectsBlankMeasureCode_400() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "",
                "measureLabel", "Bad Code",
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 1.0,
                "upperBound", 2.0
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}

