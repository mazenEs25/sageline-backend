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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ValidationMeasureControllerCreateTest extends PostgresTestcontainer {

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
        ProductionLine line = seed.seedLine("CRT");
        ValidationZone zone = seed.seedZone("ZONE_CRT_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        validationId = ticket.getId();
    }

    @Test
    void createMeasure_returnsOk_forInRangeValue() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "PWR_2G",
                "measureLabel", "Puissance 2G",
                "category", "POWER",
                "unit", "dBm",
                "lowerBound", 13.5,
                "upperBound", 16.5,
                "measuredValue", 15.5
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.deviationPct", closeTo(33.33, 0.5)));
    }

    @Test
    void createMeasure_returnsOutOfRange_forOutOfRangeValue() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "PWR_5G",
                "measureLabel", "Puissance 5G",
                "category", "POWER",
                "unit", "dBm",
                "lowerBound", 13.5,
                "upperBound", 16.5,
                "measuredValue", 20.0
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_RANGE"));
    }

    @Test
    void createMeasure_returnsNotExecuted_whenMeasuredIsNull() throws Exception {
        String body = """
                {"measureCode":"SNR_2G","measureLabel":"SNR 2G","category":"OTHER",
                "unit":"dB","lowerBound":13.5,"upperBound":16.5,"measuredValue":null}
                """;

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NOT_EXECUTED"))
                .andExpect(jsonPath("$.deviationPct").doesNotExist());
    }

    @Test
    void createMeasure_rejectsInvertedBounds_400() throws Exception {
        Map<String, Object> body = Map.of(
                "measureCode", "BAD_BOUNDS",
                "measureLabel", "Bad",
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 10.0,
                "upperBound", 5.0
        );

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMeasure_rejectsSourceLogFile_400() throws Exception {
        // spring.jackson.deserialization.fail-on-unknown-properties=true rejects unknown fields
        String body = """
                {"measureCode":"SLF_TEST","measureLabel":"SLF","category":"OTHER",
                "unit":"V","lowerBound":1.0,"upperBound":2.0,"sourceLogFile":"some.log"}
                """;

        mockMvc.perform(post("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
