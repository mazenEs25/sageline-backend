package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;

import java.util.List;

public interface ValidationMeasureService {

    List<ValidationMeasureResponse> listByValidation(Long validationId);

    ValidationMeasureResponse create(Long validationId, CreateMeasureRequest req);

    List<ValidationMeasureResponse> batchCreate(Long validationId, BatchCreateMeasureRequest req);

    List<ValidationMeasureResponse> instantiateFromCatalog(Long validationId);

    ValidationMeasureResponse update(Long validationId, Long measureId, UpdateMeasureRequest req);

    void delete(Long validationId, Long measureId);
}
