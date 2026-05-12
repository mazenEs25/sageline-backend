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
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ValidationMeasureControllerListUpdateDeleteTest extends PostgresTestcontainer {

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
        ProductionLine line = seed.seedLine("LUD");
        ValidationZone zone = seed.seedZone("ZONE_LUD_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line);
        ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        validationId = ticket.getId();
    }

    private ValidationMeasure saveMeasure(String code, double measured, double lower, double upper) {
        MeasureStatus status = measured >= lower && measured <= upper
                ? MeasureStatus.OK : MeasureStatus.OUT_OF_RANGE;
        double center = (lower + upper) / 2.0;
        double halfRange = (upper - lower) / 2.0;
        double dev = Math.abs(measured - center) / halfRange * 100.0;
        return measureRepository.save(ValidationMeasure.builder()
                .validation(ticket)
                .measureCode(code)
                .measureLabel(code)
                .category(MeasureCategory.OTHER)
                .unit("dBm")
                .lowerBound(lower)
                .upperBound(upper)
                .measuredValue(measured)
                .status(status)
                .deviationPct(dev)
                .measuredAt(Instant.now())
                .build());
    }

    @Test
    void list_returnsAllMeasuresForTicket() throws Exception {
        saveMeasure("CODE_A_" + System.nanoTime(), 15.5, 13.5, 16.5);
        saveMeasure("CODE_B_" + System.nanoTime(), 15.5, 13.5, 16.5);
        saveMeasure("CODE_C_" + System.nanoTime(), 15.5, 13.5, 16.5);

        mockMvc.perform(get("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void update_recomputesStatusAndDeviation() throws Exception {
        ValidationMeasure m = saveMeasure("UPD_CODE_" + System.nanoTime(), 15.5, 13.5, 16.5);

        Map<String, Object> req = Map.of("measuredValue", 20.0);
        mockMvc.perform(put("/api/validations/{vid}/measures/{mid}", validationId, m.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.deviationPct", closeTo(433.33, 1.0)));
    }

    @Test
    void update_nullingMeasuredValue_transitionsToNotExecuted() throws Exception {
        ValidationMeasure m = saveMeasure("NULL_CODE_" + System.nanoTime(), 15.5, 13.5, 16.5);

        String req = "{\"measuredValue\":null}";
        mockMvc.perform(put("/api/validations/{vid}/measures/{mid}", validationId, m.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_EXECUTED"))
                .andExpect(jsonPath("$.deviationPct").doesNotExist());
    }

    @Test
    void delete_removesMeasure() throws Exception {
        ValidationMeasure m = saveMeasure("DEL_CODE_" + System.nanoTime(), 15.5, 13.5, 16.5);

        mockMvc.perform(delete("/api/validations/{vid}/measures/{mid}", validationId, m.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/validations/{id}/measures", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(jsonPath("$[?(@.id == " + m.getId() + ")]", hasSize(0)));
    }

    @Test
    void update_rejectsWhenTicketNotEnCours_422() throws Exception {
        ProductionLine line2 = seed.seedLine("LUD_REV");
        ValidationZone zone2 = seed.seedZone("ZONE_LUD_REV_" + System.nanoTime(), PosteType.WIFI_CONDUIT, line2);
        Validation reviewTicket = seed.seedTicketInStatus(TicketStatus.EN_REVUE, line2, zone2);
        ValidationMeasure m = measureRepository.save(ValidationMeasure.builder()
                .validation(reviewTicket)
                .measureCode("REV_CODE")
                .measureLabel("Rev")
                .category(MeasureCategory.OTHER)
                .unit("dBm")
                .lowerBound(13.5)
                .upperBound(16.5)
                .measuredValue(15.5)
                .status(MeasureStatus.OK)
                .deviationPct(33.33)
                .measuredAt(Instant.now())
                .build());

        Map<String, Object> req = Map.of("measuredValue", 20.0);
        mockMvc.perform(put("/api/validations/{vid}/measures/{mid}", reviewTicket.getId(), m.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }
}

