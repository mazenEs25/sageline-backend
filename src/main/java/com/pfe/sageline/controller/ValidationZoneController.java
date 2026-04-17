package com.pfe.sageline.controller;


import com.pfe.sageline.dtos.request.ValidationZoneRequestDTO;
import com.pfe.sageline.dtos.response.ValidationZoneResponseDTO;
import com.pfe.sageline.service.ValidationZoneService;
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
@RequestMapping("/api/zones")
@RequiredArgsConstructor
@Tag(name = "Validation Zone Management", description = "Endpoints pour la gestion des zones de validation")
@CrossOrigin(origins = "*")
public class ValidationZoneController {

    private final ValidationZoneService validationZoneService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL','TECH_PREP')")
    @Operation(summary = "Récupérer toutes les zones de validation")
    public ResponseEntity<List<ValidationZoneResponseDTO>> getAllZones() {
        List<ValidationZoneResponseDTO> zones = validationZoneService.getAllValidationZones();
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL','TECH_PREP')")
    @Operation(summary = "Récupérer une zone par ID")
    public ResponseEntity<ValidationZoneResponseDTO> getZoneById(@PathVariable Long id) {
        ValidationZoneResponseDTO zone = validationZoneService.getValidationZoneById(id);
        return ResponseEntity.ok(zone);
    }

    @GetMapping("/line/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL','TECH_PREP')")
    @Operation(summary = "Récupérer les zones d'une ligne de production")
    public ResponseEntity<List<ValidationZoneResponseDTO>> getZonesByLine(@PathVariable Long lineId) {
        List<ValidationZoneResponseDTO> zones = validationZoneService.getValidationZonesByProductionLine(lineId);
        return ResponseEntity.ok(zones);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Créer une nouvelle zone de validation")
    public ResponseEntity<ValidationZoneResponseDTO> createZone(@Valid @RequestBody ValidationZoneRequestDTO request) {
        ValidationZoneResponseDTO createdZone = validationZoneService.createValidationZone(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdZone);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Modifier une zone de validation")
    public ResponseEntity<ValidationZoneResponseDTO> updateZone(
            @PathVariable Long id,
            @Valid @RequestBody ValidationZoneRequestDTO request) {
        ValidationZoneResponseDTO updatedZone = validationZoneService.updateValidationZone(id, request);
        return ResponseEntity.ok(updatedZone);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Supprimer une zone de validation")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        validationZoneService.deleteValidationZone(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/validations/count")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Compter les validations d'une zone")
    public ResponseEntity<Long> countValidationsByZone(@PathVariable Long id) {
        Long count = validationZoneService.countValidationsByZone(id);
        return ResponseEntity.ok(count);
    }

}
