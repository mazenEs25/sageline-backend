package com.pfe.sageline.controller;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.entity.PosteMeasureCatalog;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.repository.PosteMeasureCatalogRepository;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import com.pfe.sageline.testsupport.ValidationMeasureTestSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ValidationMeasureControllerFromTemplateTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired PosteMeasureCatalogRepository catalogRepository;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;
    private Long validationId;
    private static final PosteType POSTE_TYPE = PosteType.WIFI_CONDUIT;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        ProductionLine line = seed.seedLine("FT");
        ValidationZone zone = seed.seedZone("ZONE_FT_" + System.nanoTime(), POSTE_TYPE, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        validationId = ticket.getId();
        for (int i = 1; i <= 16; i++) {
            seed.seedCatalogRow(POSTE_TYPE, "TPL_" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        }
    }

    @Test
    void instantiate_createsOneMeasurePerActiveTemplate() throws Exception {
        mockMvc.perform(post("/api/validations/{id}/measures/from-template", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(16))))
                .andExpect(jsonPath("$[0].status").value("NOT_EXECUTED"))
                .andExpect(jsonPath("$[0].catalogTemplateId", notNullValue()));
    }

    @Test
    void instantiate_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/validations/{id}/measures/from-template", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/validations/{id}/measures/from-template", validationId)
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void instantiate_returnsEmpty_whenCatalogEmpty() throws Exception {
        ProductionLine line2 = seed.seedLine("FT_EMPTY");
        ValidationZone zone2 = seed.seedZone("ZONE_FT_EMPTY_" + System.nanoTime(), PosteType.BPO, line2);
        Validation emptyTicket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line2, zone2);

        mockMvc.perform(post("/api/validations/{id}/measures/from-template", emptyTicket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void instantiate_rejectsTicketNotInEnCours_422() throws Exception {
        ProductionLine line3 = seed.seedLine("FT_REV");
        ValidationZone zone3 = seed.seedZone("ZONE_FT_REV_" + System.nanoTime(), POSTE_TYPE, line3);
        Validation reviewTicket = seed.seedTicketInStatus(TicketStatus.EN_REVUE, line3, zone3);

        mockMvc.perform(post("/api/validations/{id}/measures/from-template", reviewTicket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void instantiate_skipsInactiveTemplates() throws Exception {
        ProductionLine line4 = seed.seedLine("FT_INACTIVE");
        ValidationZone zone4 = seed.seedZone("ZONE_FT_INACTIVE_" + System.nanoTime(), PosteType.BANC_RX_TX, line4);
        Validation ticket4 = seed.seedTicketInStatus(TicketStatus.EN_COURS, line4, zone4);

        for (int i = 1; i <= 12; i++) {
            seed.seedCatalogRow(PosteType.BANC_RX_TX, "ACT_" + i + "_" + System.nanoTime(), 5.0, 10.0, "V", true);
        }
        for (int i = 1; i <= 4; i++) {
            PosteMeasureCatalog inactive = seed.seedCatalogRow(PosteType.BANC_RX_TX,
                    "INACT_" + i + "_" + System.nanoTime(), 5.0, 10.0, "V", true);
            inactive.setActive(false);
            catalogRepository.save(inactive);
        }

        mockMvc.perform(post("/api/validations/{id}/measures/from-template", ticket4.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)));
    }
}
