package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.BatchUpdateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.BatchValidationMeasureResponse;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;

import java.util.List;

public interface ValidationMeasureService {

    List<ValidationMeasureResponse> listByValidation(Long validationId);

    /**
     * Per-poste measure list: all measures attached to the poste of the line
     * identified by (validationId, zoneId).
     */
    List<ValidationMeasureResponse> listByPoste(Long validationId, Long zoneId);

    ValidationMeasureResponse create(Long validationId, CreateMeasureRequest req);

    List<ValidationMeasureResponse> batchCreate(Long validationId, BatchCreateMeasureRequest req);

    /**
     * Bulk-update {@code measuredValue} on a set of existing measures.
     * Per-row success/failure is reported in the structured response so the UI
     * can highlight failing rows without rejecting the whole batch.
     */
    BatchValidationMeasureResponse batchUpdate(Long validationId, BatchUpdateMeasureRequest req);

    List<ValidationMeasureResponse> instantiateFromCatalog(Long validationId);

    /**
     * Instantiate ONE catalog template onto a ticket as a NOT_EXECUTED measure.
     * Idempotent: if a measure already exists for the same template on this ticket,
     * the existing row is returned without creating a duplicate.
     */
    ValidationMeasureResponse instantiateOneFromCatalog(Long validationId, Long templateId);

    ValidationMeasureResponse update(Long validationId, Long measureId, UpdateMeasureRequest req);

    void delete(Long validationId, Long measureId);
}
