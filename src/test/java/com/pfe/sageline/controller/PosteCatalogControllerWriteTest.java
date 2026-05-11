package com.pfe.sageline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogUpdateRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogBatchRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.DuplicateCatalogTemplateException;
import com.pfe.sageline.exception.BoundsViolationException;
import com.pfe.sageline.service.PosteCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(PosteCatalogController.class)
public class PosteCatalogControllerWriteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PosteCatalogService posteCatalogService;

    @Autowired
    private ObjectMapper objectMapper;

    private PosteMeasureCatalogResponse createSampleResponse(Long id) {
        return new PosteMeasureCatalogResponse(
            id,
            PosteType.TEST_FONCTIONNEL,
            "TEST_CODE",
            "Test Measure",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
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
    public void createHappyPath_returns201() throws Exception {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "NEW_CODE",
            "New Measure",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        PosteMeasureCatalogResponse response = createSampleResponse(1L);

        org.mockito.Mockito.when(posteCatalogService.create(org.mockito.ArgumentMatchers.any()))
            .thenReturn(response);

        mockMvc.perform(
            post("/api/poste-catalog/measures")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    public void createAsExpert_returns403() throws Exception {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "NEW_CODE",
            "New Measure",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        mockMvc.perform(
            post("/api/poste-catalog/measures")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isForbidden());
    }

    @Test
    public void createDuplicate_returns409() throws Exception {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "DUP_CODE",
            "Duplicate Measure",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        org.mockito.Mockito.when(posteCatalogService.create(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new DuplicateCatalogTemplateException(List.of("DUP_CODE")));

        mockMvc.perform(
            post("/api/poste-catalog/measures")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.conflictingCodes", hasItem("DUP_CODE")));
    }

    @Test
    public void createInvertedBounds_returns422() throws Exception {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "BAD_BOUNDS",
            "Bad Bounds Measure",
            MeasureCategory.POWER,
            "dBm",
            10.0,
            -20.0,
            true,
            1,
            null,
            null,
            null
        );

        org.mockito.Mockito.when(posteCatalogService.create(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new BoundsViolationException("Lower bound must be less than upper bound"));

        mockMvc.perform(
            post("/api/poste-catalog/measures")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isUnprocessableEntity());
    }

    @Test
    public void updateHappyPath_returns200() throws Exception {
        PosteMeasureCatalogUpdateRequest request = new PosteMeasureCatalogUpdateRequest(
            "Updated Label",
            MeasureCategory.VOLTAGE,
            "V",
            -100.0,
            100.0,
            false,
            1,
            null,
            null,
            null,
            null
        );

        PosteMeasureCatalogResponse response = createSampleResponse(1L);

        org.mockito.Mockito.when(posteCatalogService.update(org.mockito.eq(1L), org.mockito.any()))
            .thenReturn(response);

        mockMvc.perform(
            put("/api/poste-catalog/measures/1")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk());
    }

    @Test
    public void softDelete_returns204() throws Exception {
        org.mockito.Mockito.doNothing().when(posteCatalogService).softDelete(1L);

        mockMvc.perform(
            delete("/api/poste-catalog/measures/1")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
        )
        .andExpect(status().isNoContent());
    }

    @Test
    public void getById_returns200() throws Exception {
        PosteMeasureCatalogResponse response = createSampleResponse(1L);

        org.mockito.Mockito.when(posteCatalogService.getById(1L))
            .thenReturn(response);

        mockMvc.perform(
            get("/api/poste-catalog/measures/1")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    public void unauthenticatedRequest_returns401() throws Exception {
        PosteMeasureCatalogRequest request = new PosteMeasureCatalogRequest(
            PosteType.TEST_FONCTIONNEL,
            "NEW_CODE",
            "New Measure",
            MeasureCategory.POWER,
            "dBm",
            -20.0,
            10.0,
            true,
            1,
            null,
            null,
            null
        );

        mockMvc.perform(
            post("/api/poste-catalog/measures")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isUnauthorized());
    }
}
