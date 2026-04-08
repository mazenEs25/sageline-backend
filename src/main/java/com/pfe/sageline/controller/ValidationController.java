package com.pfe.sageline.controller;


import com.pfe.sageline.dtos.ValidationRequestDTO;
import com.pfe.sageline.dtos.ValidationResponseDTO;
import com.pfe.sageline.entity.ValidationStatus;
import com.pfe.sageline.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/validations")
@RequiredArgsConstructor
@Tag(name = "Validation Management", description = "Endpoints pour la gestion des validations")
@CrossOrigin(origins = "*")
public class ValidationController {

    private final ValidationService validationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer toutes les validations")
    public ResponseEntity<List<ValidationResponseDTO>> getAllValidations() {
        List<ValidationResponseDTO> validations = validationService.getAllValidations();
        return ResponseEntity.ok(validations);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer une validation par ID")
    public ResponseEntity<ValidationResponseDTO> getValidationById(@PathVariable Long id) {
        ValidationResponseDTO validation = validationService.getValidationById(id);
        return ResponseEntity.ok(validation);
    }

    @GetMapping("/zone/{zoneId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer les validations d'une zone")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByZone(@PathVariable Long zoneId) {
        List<ValidationResponseDTO> validations = validationService.getValidationsByZone(zoneId);
        return ResponseEntity.ok(validations);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer les validations par statut")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByStatus(@PathVariable ValidationStatus status) {
        List<ValidationResponseDTO> validations = validationService.getValidationsByStatus(status);
        return ResponseEntity.ok(validations);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer les validations en cours")
    public ResponseEntity<List<ValidationResponseDTO>> getActiveValidations() {
        List<ValidationResponseDTO> validations = validationService.getActiveValidations();
        return ResponseEntity.ok(validations);
    }

    @GetMapping("/line/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les validations d'une ligne de production")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByLine(@PathVariable Long lineId) {
        List<ValidationResponseDTO> validations = validationService.getValidationsByProductionLine(lineId);
        return ResponseEntity.ok(validations);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les validations par plage de dates")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<ValidationResponseDTO> validations = validationService.getValidationsByDateRange(start, end);
        return ResponseEntity.ok(validations);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECH_VALIDATION', 'CHEF_SECTEUR')")
    @Operation(summary = "Lancer une nouvelle validation")
    public ResponseEntity<ValidationResponseDTO> startValidation(@Valid @RequestBody ValidationRequestDTO request) {
        ValidationResponseDTO validation = validationService.startValidation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(validation);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECH_VALIDATION', 'CHEF_SECTEUR')")
    @Operation(summary = "Modifier une validation")
    public ResponseEntity<ValidationResponseDTO> updateValidation(
            @PathVariable Long id,
            @Valid @RequestBody ValidationRequestDTO request) {
        ValidationResponseDTO validation = validationService.updateValidation(id, request);
        return ResponseEntity.ok(validation);
    }


    @PutMapping("/close/{id}")
    @PreAuthorize("hasAnyRole('TECH_VALIDATION', 'CHEF_SECTEUR')")
    public ResponseEntity<ValidationResponseDTO> closeValidation(
            @PathVariable Long id,
            @RequestParam ValidationStatus finalStatus,
            @RequestParam(required = false) String comments) {

        ValidationResponseDTO validation =
                validationService.closeValidation(id, finalStatus, comments);

        return ResponseEntity.ok(validation);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Supprimer une validation")
    public ResponseEntity<Void> deleteValidation(@PathVariable Long id) {
        validationService.deleteValidation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/with-results")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VALIDATION')")
    @Operation(summary = "Récupérer une validation avec tous ses résultats")
    public ResponseEntity<ValidationResponseDTO> getValidationWithResults(@PathVariable Long id) {
        ValidationResponseDTO validation = validationService.getValidationById(id);
        return ResponseEntity.ok(validation);
    }
}
