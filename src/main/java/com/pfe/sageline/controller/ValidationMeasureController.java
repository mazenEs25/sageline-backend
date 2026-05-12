package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.service.ValidationMeasureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/validations/{validationId}/measures")
@RequiredArgsConstructor
public class ValidationMeasureController {

    private final ValidationMeasureService service;

    @GetMapping
    public List<ValidationMeasureResponse> list(@PathVariable Long validationId) {
        return service.listByValidation(validationId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
    public ResponseEntity<ValidationMeasureResponse> create(
            @PathVariable Long validationId,
            @Valid @RequestBody CreateMeasureRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(validationId, req));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
    public ResponseEntity<List<ValidationMeasureResponse>> batchCreate(
            @PathVariable Long validationId,
            @Valid @RequestBody BatchCreateMeasureRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.batchCreate(validationId, req));
    }

    @PostMapping("/from-template")
    @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
    public List<ValidationMeasureResponse> instantiateFromCatalog(@PathVariable Long validationId) {
        return service.instantiateFromCatalog(validationId);
    }

    @PutMapping("/{measureId}")
    @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
    public ValidationMeasureResponse update(
            @PathVariable Long validationId,
            @PathVariable Long measureId,
            @Valid @RequestBody UpdateMeasureRequest req) {
        return service.update(validationId, measureId, req);
    }

    @DeleteMapping("/{measureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TECH_VAL','TECH_PREP','ADMIN_IT')")
    public void delete(@PathVariable Long validationId, @PathVariable Long measureId) {
        service.delete(validationId, measureId);
    }
}
