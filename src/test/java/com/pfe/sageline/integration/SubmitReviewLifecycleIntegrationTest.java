package com.pfe.sageline.integration;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationMeasure;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.ValidationMeasureRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.service.ValidationMeasureService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SubmitReviewLifecycleIntegrationTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ValidationRepository validationRepository;
    @Autowired ValidationMeasureService measureService;
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

    private long seedTicketWith16MandatoryMeasures(TicketStatus status) {
        ProductionLine line = seed.seedLine("SR");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_SR_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(status, line, zone);
        for (int i = 1; i <= 16; i++) {
            seed.seedCatalogRow(pt, "SR_M" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        }
        measureService.instantiateFromCatalog(ticket.getId());
        return ticket.getId();
    }

    @Test
    void blocked_when_two_mandatory_missing() throws Exception {
        long ticketId = seedTicketWith16MandatoryMeasures(TicketStatus.EN_COURS);

        // Fill 14 of the 16 measures (leave 2 as NOT_EXECUTED)
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        assertThat(measures).hasSize(16);
        measures.stream().limit(14).forEach(m -> {
            m.setMeasuredValue(12.0);
            m.setStatus(MeasureStatus.OK);
            m.setDeviationPct(40.0);
            measureRepository.save(m);
        });

        mockMvc.perform(patch("/api/validations/{id}/submit-review", ticketId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.canTransition").value(false))
                .andExpect(jsonPath("$.mandatoryTotal").value(16))
                .andExpect(jsonPath("$.mandatoryFilled").value(14))
                .andExpect(jsonPath("$.mandatoryMissing").value(2))
                .andExpect(jsonPath("$.missingMeasures", hasSize(2)))
                .andExpect(jsonPath("$.missingMeasures[0].measureCode", notNullValue()));

        // Ticket must still be EN_COURS
        Validation after = validationRepository.findById(ticketId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_COURS);
    }

    @Test
    void succeeds_after_filling_all_mandatory() throws Exception {
        long ticketId = seedTicketWith16MandatoryMeasures(TicketStatus.EN_COURS);

        // Fill all 16 measures
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticketId);
        measures.forEach(m -> {
            m.setMeasuredValue(12.0);
            m.setStatus(MeasureStatus.OK);
            m.setDeviationPct(40.0);
            measureRepository.save(m);
        });

        mockMvc.perform(patch("/api/validations/{id}/submit-review", ticketId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk());

        Validation after = validationRepository.findById(ticketId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_REVUE);
    }

    @Test
    void succeeds_when_zero_mandatory_in_catalog() throws Exception {
        ProductionLine line = seed.seedLine("ZM");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_ZM_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        // Seed only non-mandatory catalog rows
        for (int i = 1; i <= 3; i++) {
            seed.seedCatalogRow(pt, "ZM_OPT" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", false);
        }
        measureService.instantiateFromCatalog(ticket.getId());

        mockMvc.perform(patch("/api/validations/{id}/submit-review", ticket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk());

        Validation after = validationRepository.findById(ticket.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_REVUE);
    }

    @Test
    void wrong_source_status_returns_422() throws Exception {
        // A ticket in EN_REVUE cannot be submitted for review again.
        // With assertStatus() removed, SourceStatusRule handles this and produces 422 + WorkflowReadinessDTO.
        ProductionLine line = seed.seedLine("WS");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_WS_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_REVUE, line, zone);

        mockMvc.perform(patch("/api/validations/{id}/submit-review", ticket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.canTransition").value(false))
                .andExpect(jsonPath("$.blockingReasons[0]", containsString("is not eligible for transition to EN_REVUE")));

        // Ticket must remain unchanged — guard must not mutate state
        Validation after = validationRepository.findById(ticket.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.EN_REVUE);
    }
}
