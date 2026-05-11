package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.PosteMeasureCatalogBatchRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogUpdateRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.enums.PosteType;

import java.util.List;

public interface PosteCatalogService {

    // List operations
    List<PosteMeasureCatalogResponse> listAll(PosteType filter, boolean includeInactive);

    List<PosteMeasureCatalogResponse> getByPosteType(PosteType posteType, boolean includeInactive);

    List<PosteMeasureCatalogResponse> getMeasuresByPosteType(PosteType posteType);

    List<PosteType> listPostesWithActive();

    // Get single
    PosteMeasureCatalogResponse getById(Long id);

    // Mutations
    PosteMeasureCatalogResponse create(PosteMeasureCatalogRequest request);

    PosteMeasureCatalogResponse update(Long id, PosteMeasureCatalogUpdateRequest request);

    void softDelete(Long id);

    // Batch operation
    List<PosteMeasureCatalogResponse> batchCreate(PosteMeasureCatalogBatchRequest request);
}
