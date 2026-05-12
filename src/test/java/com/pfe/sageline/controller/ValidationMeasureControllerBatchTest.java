package com.pfe.sageline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ValidationMeasureControllerBatchTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;
    private Long validationId;
    private Validation ticket;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        ProductionLine line = seed.seedLine("BAT");
        ValidationZone zone = seed.seedZone("ZONE_BAT_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        validationId = ticket.getId();
    }

    private Map<String, Object> validEntry(String code) {
        return Map.of(
                "measureCode", code,
                "measureLabel", code,
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 1.0,
                "upperBound", 2.0
        );
    }

    @Test
    void batch_createsAllEntries_onAllValid() throws Exception {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            entries.add(validEntry("BAT_CODE_" + i + "_" + System.nanoTime()));
        }
        Map<String, Object> body = Map.of("measures", entries);

        mockMvc.perform(post("/api/validations/{id}/measures/batch", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void batch_rejectsAll_whenOneEntryHasUnknownTemplate() throws Exception {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            entries.add(validEntry("BTPL_VALID_" + i + "_" + System.nanoTime()));
        }
        Map<String, Object> invalidEntry = Map.of(
                "templateId", 999999L,
                "measureCode", "ANY",
                "measureLabel", "Any",
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 1.0,
                "upperBound", 2.0
        );
        entries.add(invalidEntry);
        Map<String, Object> body = Map.of("measures", entries);

        mockMvc.perform(post("/api/validations/{id}/measures/batch", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("BATCH_REJECTED"))
                .andExpect(jsonPath("$.failedEntries[0].index").value(3))
                .andExpect(jsonPath("$.failedEntries[0].code").value("UNKNOWN_TEMPLATE"));
    }

    @Test
    void batch_rejectsAll_whenOneEntryDuplicatesAnExisting() throws Exception {
        // pre-seed a measure with code DUPCODE
        String dupCode = "DUPCODE_" + System.nanoTime();
        measureRepository.save(ValidationMeasure.builder()
                .validation(ticket)
                .measureCode(dupCode)
                .measureLabel(dupCode)
                .category(MeasureCategory.OTHER)
                .unit("V")
                .lowerBound(1.0)
                .upperBound(2.0)
                .measuredValue(null)
                .status(MeasureStatus.NOT_EXECUTED)
                .deviationPct(null)
                .measuredAt(Instant.now())
                .build());

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(validEntry("DUP_NEW_1_" + System.nanoTime()));
        entries.add(Map.of(
                "measureCode", dupCode,
                "measureLabel", dupCode,
                "category", "OTHER",
                "unit", "V",
                "lowerBound", 1.0,
                "upperBound", 2.0
        ));
        Map<String, Object> body = Map.of("measures", entries);

        mockMvc.perform(post("/api/validations/{id}/measures/batch", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failedEntries[0].code").value("DUPLICATE_MEASURE_CODE"));
    }

    @Test
    void batch_rejectsAll_whenTicketNotInEnCours() throws Exception {
        ProductionLine line2 = seed.seedLine("BAT_REV");
        ValidationZone zone2 = seed.seedZone("ZONE_BAT_REV_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line2);
        Validation reviewTicket = seed.seedTicketInStatus(TicketStatus.EN_REVUE, line2, zone2);

        List<Map<String, Object>> entries = List.of(
                validEntry("X1_" + System.nanoTime()),
                validEntry("X2_" + System.nanoTime()),
                validEntry("X3_" + System.nanoTime())
        );
        Map<String, Object> body = Map.of("measures", entries);

        // The ticket is not EN_COURS; batch should reject with a global -1 entry
        mockMvc.perform(post("/api/validations/{id}/measures/batch", reviewTicket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failedEntries[0].index").value(-1))
                .andExpect(jsonPath("$.failedEntries[0].code").value("TICKET_NOT_EDITABLE"));
    }

    @Test
    void batch_rejectsEmptyMeasuresArray_400() throws Exception {
        String body = "{\"measures\":[]}";

        mockMvc.perform(post("/api/validations/{id}/measures/batch", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

