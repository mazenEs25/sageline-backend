package com.pfe.sageline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogBatchRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.exception.BatchValidationException;
import com.pfe.sageline.exception.DuplicateCatalogTemplateException;
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
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class PosteCatalogControllerBatchTest extends PostgresTestcontainer {

    @Autowired WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean PosteCatalogService posteCatalogService;
    @MockitoBean SecurityUtils securityUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private PosteMeasureCatalogResponse createResponse(Long id, String code) {
        return new PosteMeasureCatalogResponse(
            id,
            PosteType.TEST_FONCTIONNEL,
            code,
            "Label for " + code,
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

    private PosteMeasureCatalogBatchRequest.BatchItem createBatchItem(String code) {
        return new PosteMeasureCatalogBatchRequest.BatchItem(
            code,
            "Label for " + code,
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
    }

    @Test
    public void batch_happyPath_returns201_withAllIds() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(createBatchItem("CODE_" + i));
        }

        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        List<PosteMeasureCatalogResponse> responses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            responses.add(createResponse((long) (i + 1), "CODE_" + i));
        }

        org.mockito.Mockito.when(posteCatalogService.batchCreate(org.mockito.ArgumentMatchers.any()))
            .thenReturn(responses);

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$", hasSize(5)))
        .andExpect(jsonPath("$[0].id", notNullValue()));
    }

    @Test
    public void batch_resubmit_returns409_listsAllCodes_noNewRows() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            items.add(createBatchItem("CODE_" + i));
        }

        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        org.mockito.Mockito.when(posteCatalogService.batchCreate(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new DuplicateCatalogTemplateException(List.of("CODE_0", "CODE_1", "CODE_2")));

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.conflictingCodes", hasSize(3)))
        .andExpect(jsonPath("$.conflictingCodes", hasItems("CODE_0", "CODE_1", "CODE_2")));
    }

    @Test
    public void batch_invertedBoundsInOneItem_returns422_zeroRowsPersisted() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = List.of(
            createBatchItem("CODE_0"),
            new PosteMeasureCatalogBatchRequest.BatchItem(
                "CODE_1",
                "Bad Bounds",
                MeasureCategory.POWER,
                "dBm",
                10.0,
                -20.0,
                true,
                1,
                null,
                null,
                null
            )
        );

        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        List<BatchValidationException.BatchValidationError> errors = List.of(
            new BatchValidationException.BatchValidationError(1, "bounds", "BOUNDS_VIOLATION", "Lower bound must be less than upper bound")
        );

        org.mockito.Mockito.when(posteCatalogService.batchCreate(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new BatchValidationException(errors));

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].index", is(1)));
    }

    @Test
    public void batch_inBatchDuplicateCode_returns422_bothIndexesFlagged() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = List.of(
            createBatchItem("CODE_0"),
            createBatchItem("CODE_0")
        );

        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        List<BatchValidationException.BatchValidationError> errors = List.of(
            new BatchValidationException.BatchValidationError(1, "measureCode", "DUPLICATE_CODE", "Duplicate measure code in batch")
        );

        org.mockito.Mockito.when(posteCatalogService.batchCreate(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new BatchValidationException(errors));

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    public void batch_asExpert_returns403() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = List.of(createBatchItem("CODE_0"));
        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_EXPERT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isForbidden());
    }

    @Test
    public void batch_over100Items_returns422() throws Exception {
        List<PosteMeasureCatalogBatchRequest.BatchItem> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(createBatchItem("CODE_" + i));
        }

        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            items
        );

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    public void batch_emptyItems_returns422() throws Exception {
        PosteMeasureCatalogBatchRequest request = new PosteMeasureCatalogBatchRequest(
            PosteType.TEST_FONCTIONNEL,
            List.of()
        );

        mockMvc.perform(
            post("/api/poste-catalog/measures/batch")
                .with(jwt().authorities(() -> "ROLE_ADMIN_IT"))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest());
    }
}
