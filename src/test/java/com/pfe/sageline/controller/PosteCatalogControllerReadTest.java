package com.pfe.sageline.controller;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.service.PosteCatalogService;
import com.pfe.sageline.testsupport.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class PosteCatalogControllerReadTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    @MockitoBean PosteCatalogService posteCatalogService;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private PosteMeasureCatalogResponse createResponse(Long id, String code, PosteType poste, int displayOrder) {
        return new PosteMeasureCatalogResponse(
            id,
            poste,
            code,
            "Label for " + code,
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            displayOrder,
            true,
            null,
            null,
            null,
            Instant.now(),
            1L,
            Instant.now(),
            1L
        );
    }

    @Test
    public void listAll_returnsAllActiveByDefault() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1),
            createResponse(2L, "CODE2", PosteType.WIFI_CONDUIT, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.listAll(null, false))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    public void listAll_includeInactiveTrue_returnsAll() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1),
            createResponse(2L, "CODE2", PosteType.WIFI_CONDUIT, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.listAll(null, true))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog?includeInactive=true")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    public void listAll_filterByPosteType() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.listAll(PosteType.TEST_FONCTIONNEL, false))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog?posteType=TEST_FONCTIONNEL")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void getByPosteType_unknownPoste_returnsEmptyArray() throws Exception {
        org.mockito.Mockito.when(posteCatalogService.getByPosteType(PosteType.WIFI_RY, false))
            .thenReturn(List.of());

        mockMvc.perform(
            get("/api/poste-catalog/WIFI_RY")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getByPosteType_returnsSortedByDisplayOrder() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1),
            createResponse(2L, "CODE2", PosteType.TEST_FONCTIONNEL, 2),
            createResponse(3L, "CODE3", PosteType.TEST_FONCTIONNEL, 3)
        );

        org.mockito.Mockito.when(posteCatalogService.getByPosteType(PosteType.TEST_FONCTIONNEL, false))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog/TEST_FONCTIONNEL")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].displayOrder", is(1)))
        .andExpect(jsonPath("$[1].displayOrder", is(2)))
        .andExpect(jsonPath("$[2].displayOrder", is(3)));
    }

    @Test
    public void getMeasuresByPosteType_returnsMeasuresOnly() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.getMeasuresByPosteType(PosteType.TEST_FONCTIONNEL))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog/TEST_FONCTIONNEL/measures")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void listPostesWithActive_returnsDistinctList() throws Exception {
        List<PosteType> responses = List.of(PosteType.TEST_FONCTIONNEL, PosteType.WIFI_CONDUIT, PosteType.ACC);

        org.mockito.Mockito.when(posteCatalogService.listPostesWithActive())
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog/postes")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    public void readAsTechVal_returns200() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.listAll(null, false))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog")
                .with(jwt().authorities(() -> "ROLE_TECH_VAL"))
        )
        .andExpect(status().isOk());
    }

    @Test
    public void readAsResponsable_returns200() throws Exception {
        List<PosteMeasureCatalogResponse> responses = List.of(
            createResponse(1L, "CODE1", PosteType.TEST_FONCTIONNEL, 1)
        );

        org.mockito.Mockito.when(posteCatalogService.listAll(null, false))
            .thenReturn(responses);

        mockMvc.perform(
            get("/api/poste-catalog")
                .with(jwt().authorities(() -> "ROLE_RESPONSABLE"))
        )
        .andExpect(status().isOk());
    }

    @Test
    public void readUnauthenticated_returns401() throws Exception {
        mockMvc.perform(
            get("/api/poste-catalog")
        )
        .andExpect(status().isUnauthorized());
    }

    @Test
    public void getByIdNotFound_returns404() throws Exception {
        org.mockito.Mockito.when(posteCatalogService.getById(999L))
            .thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(
            get("/api/poste-catalog/measures/999")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
        )
        .andExpect(status().isNotFound());
    }
}
