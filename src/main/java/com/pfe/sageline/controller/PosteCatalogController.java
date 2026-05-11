package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.PosteMeasureCatalogRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogUpdateRequest;
import com.pfe.sageline.dtos.request.PosteMeasureCatalogBatchRequest;
import com.pfe.sageline.dtos.response.PosteMeasureCatalogResponse;
import com.pfe.sageline.enums.PosteType;
import com.pfe.sageline.service.PosteCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/poste-catalog")
@RequiredArgsConstructor
@Tag(name = "poste-catalog", description = "PosteType Catalog Management")
public class PosteCatalogController {

    private final PosteCatalogService posteCatalogService;

    // ─── US3 Read Endpoints ───

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all measures", description = "List all measures, optionally filtered by poste type and including inactive")
    public ResponseEntity<List<PosteMeasureCatalogResponse>> listAll(
        @Parameter(description = "Filter by poste type")
        @RequestParam(required = false) PosteType posteType,
        @Parameter(description = "Include inactive measures")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(posteCatalogService.listAll(posteType, includeInactive));
    }

    @GetMapping("/postes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all postes with active measures")
    public ResponseEntity<List<PosteType>> listPostesWithActive() {
        return ResponseEntity.ok(posteCatalogService.listPostesWithActive());
    }

    @GetMapping("/{posteType}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all measures for a poste type")
    public ResponseEntity<List<PosteMeasureCatalogResponse>> getByPosteType(
        @PathVariable PosteType posteType,
        @Parameter(description = "Include inactive measures")
        @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(posteCatalogService.getByPosteType(posteType, includeInactive));
    }

    @GetMapping("/{posteType}/measures")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get measures only for a poste type (without parent metadata)")
    public ResponseEntity<List<PosteMeasureCatalogResponse>> getMeasuresByPosteType(
        @PathVariable PosteType posteType
    ) {
        return ResponseEntity.ok(posteCatalogService.getMeasuresByPosteType(posteType));
    }

    @GetMapping("/measures/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a measure by ID")
    public ResponseEntity<PosteMeasureCatalogResponse> getById(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(posteCatalogService.getById(id));
    }

    // ─── US2 Write Endpoints ───

    @PostMapping("/measures")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Create a new measure template")
    public ResponseEntity<PosteMeasureCatalogResponse> create(
        @Valid @RequestBody PosteMeasureCatalogRequest request
    ) {
        PosteMeasureCatalogResponse response = posteCatalogService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/measures/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Update a measure template")
    public ResponseEntity<PosteMeasureCatalogResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody PosteMeasureCatalogUpdateRequest request
    ) {
        return ResponseEntity.ok(posteCatalogService.update(id, request));
    }

    @DeleteMapping("/measures/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Soft delete a measure template")
    public ResponseEntity<Void> softDelete(
        @PathVariable Long id
    ) {
        posteCatalogService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── US4 Batch Endpoints ───

    @PostMapping("/measures/batch")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Batch create measure templates")
    public ResponseEntity<List<PosteMeasureCatalogResponse>> batchCreate(
        @Valid @RequestBody PosteMeasureCatalogBatchRequest request
    ) {
        List<PosteMeasureCatalogResponse> response = posteCatalogService.batchCreate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
