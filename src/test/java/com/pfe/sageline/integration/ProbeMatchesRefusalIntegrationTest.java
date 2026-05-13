package com.pfe.sageline.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ProbeMatchesRefusalIntegrationTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @Autowired ValidationMeasureTestSeed seed;
    @Autowired ValidationMeasureRepository measureRepository;
    @Autowired ValidationMeasureService measureService;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean JwtDecoder jwtDecoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(securityUtils.getCurrentUserId()).thenReturn(null);
        when(securityUtils.getCurrentUsername()).thenReturn("test-user");
    }

    @Test
    void probe_response_equals_refusal_response() throws Exception {
        ProductionLine line = seed.seedLine("PMR");
        PosteType pt = PosteType.WIFI_CONDUIT;
        ValidationZone zone = seed.seedZone("ZONE_PMR_" + System.nanoTime(), pt, line);
        Validation ticket = seed.seedTicketInStatus(TicketStatus.EN_COURS, line, zone);
        for (int i = 1; i <= 16; i++) {
            seed.seedCatalogRow(pt, "PMR_M" + i + "_" + System.nanoTime(), 10.0, 15.0, "dBm", true);
        }
        measureService.instantiateFromCatalog(ticket.getId());

        // Fill 14 measures (2 still NOT_EXECUTED → should block)
        List<ValidationMeasure> measures = measureRepository.findAllByValidationIdFetchTemplate(ticket.getId());
        measures.stream().limit(14).forEach(m -> {
            m.setMeasuredValue(12.0);
            m.setStatus(MeasureStatus.OK);
            m.setDeviationPct(40.0);
            measureRepository.save(m);
        });

        // Hit the readiness probe
        MvcResult probeResult = mockMvc.perform(get("/api/validations/{id}/readiness", ticket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isOk())
                .andReturn();
        String probeBody = probeResult.getResponse().getContentAsString();

        // Hit submit-review → should return 422 with same body
        MvcResult refusalResult = mockMvc.perform(patch("/api/validations/{id}/submit-review", ticket.getId())
                        .with(jwt().authorities(() -> "ROLE_TECH_VAL")))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();
        String refusalBody = refusalResult.getResponse().getContentAsString();

        JsonNode probeNode = objectMapper.readTree(probeBody);
        JsonNode refusalNode = objectMapper.readTree(refusalBody);

        assertThat(probeNode).isEqualTo(refusalNode);
    }
}
