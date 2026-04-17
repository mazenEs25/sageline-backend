package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.ValidationResultRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResultResponseDTO;
import com.pfe.sageline.service.ValidationResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/validation-results")
@RequiredArgsConstructor
@Tag(name = "Validation Result Management", description = "Endpoints pour la gestion des résultats de validation")
@CrossOrigin(origins = "*")
public class ValidationResultController {

    private final ValidationResultService validationResultService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL')")
    @Operation(summary = "Récupérer tous les résultats de validation")
    public ResponseEntity<List<ValidationResultResponseDTO>> getAllResults() {
        List<ValidationResultResponseDTO> results = validationResultService.getAllValidationResults();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALN')")
    @Operation(summary = "Récupérer un résultat par ID")
    public ResponseEntity<ValidationResultResponseDTO> getResultById(@PathVariable Long id) {
        ValidationResultResponseDTO result = validationResultService.getValidationResultById(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/validation/{validationId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL')")
    @Operation(summary = "Récupérer les résultats d'une validation")
    public ResponseEntity<List<ValidationResultResponseDTO>> getResultsByValidation(@PathVariable Long validationId) {
        List<ValidationResultResponseDTO> results = validationResultService.getResultsByValidation(validationId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/conform/{conform}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les résultats conformes ou non conformes")
    public ResponseEntity<List<ValidationResultResponseDTO>> getResultsByConformity(@PathVariable Boolean conform) {
        List<ValidationResultResponseDTO> results = validationResultService.getResultsByConformity(conform);
        return ResponseEntity.ok(results);
    }



    @GetMapping("/parameter/{parameter}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les résultats par paramètre")
    public ResponseEntity<List<ValidationResultResponseDTO>> getResultsByParameter(@PathVariable String parameter) {
        List<ValidationResultResponseDTO> results = validationResultService.getResultsByParameter(parameter);
        return ResponseEntity.ok(results);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECH_VAL', 'CHEF_SECTEUR')")
    @Operation(summary = "Créer un nouveau résultat de validation")
    public ResponseEntity<ValidationResultResponseDTO> createResult(@Valid @RequestBody ValidationResultRequestDTO request) {
        ValidationResultResponseDTO result = validationResultService.addValidationResult(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TECH_VAL', 'CHEF_SECTEUR')")
    @Operation(summary = "Créer plusieurs résultats de validation en une fois")
    public ResponseEntity<List<ValidationResultResponseDTO>> createResultsBatch(
            @Valid @RequestBody List<ValidationResultRequestDTO> requests) {
        List<ValidationResultResponseDTO> results = validationResultService.addMultipleResults(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECH_VAL', 'CHEF_SECTEUR')")
    @Operation(summary = "Modifier un résultat de validation")
    public ResponseEntity<ValidationResultResponseDTO> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody ValidationResultRequestDTO request) {
        ValidationResultResponseDTO result = validationResultService.updateResult(id, request);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Supprimer un résultat de validation")
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        validationResultService.deleteValidationResult(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validation/{validationId}/non-conform/count")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL')")
    @Operation(summary = "Compter les résultats non conformes d'une validation")
    public ResponseEntity<Long> countNonConformResults(@PathVariable Long validationId) {
        Long count = validationResultService.countNonConformByValidation(validationId);
        return ResponseEntity.ok(count);
    }


}
