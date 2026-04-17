package com.pfe.sageline.controller;


import com.pfe.sageline.dtos.request.ProductionLineRequestDTO;
import com.pfe.sageline.dtos.response.ProductionLineResponseDTO;
import com.pfe.sageline.service.ProductionLineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lines")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductionLineController {

    private final ProductionLineService productionLineService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT','TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<List<ProductionLineResponseDTO>> getAllLines() {
        List<ProductionLineResponseDTO> lines = productionLineService.getAllProductionLines();
        return ResponseEntity.ok(lines);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT','TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<ProductionLineResponseDTO> getLineById(@PathVariable Long id) {
        ProductionLineResponseDTO line = productionLineService.getProductionLineById(id);
        return ResponseEntity.ok(line);
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<ProductionLineResponseDTO> getLineByCode(@PathVariable String code) {
        ProductionLineResponseDTO line = productionLineService.getProductionLineByCode(code);
        return ResponseEntity.ok(line);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<List<ProductionLineResponseDTO>> getActiveLines() {
        List<ProductionLineResponseDTO> lines = productionLineService.getActiveProductionLines();
        return ResponseEntity.ok(lines);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ProductionLineResponseDTO> createLine(@Valid @RequestBody ProductionLineRequestDTO request) {
        ProductionLineResponseDTO createdLine = productionLineService.createProductionLine(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLine);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ProductionLineResponseDTO> updateLine(
            @PathVariable Long id,
            @Valid @RequestBody ProductionLineRequestDTO request) {
        ProductionLineResponseDTO updatedLine = productionLineService.updateProductionLine(id, request);
        return ResponseEntity.ok(updatedLine);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ProductionLineResponseDTO> activateLine(@PathVariable Long id) {
        ProductionLineResponseDTO line = productionLineService.setLineActive(id, true);
        return ResponseEntity.ok(line);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ProductionLineResponseDTO> deactivateLine(@PathVariable Long id) {
        ProductionLineResponseDTO line = productionLineService.setLineActive(id, false);
        return ResponseEntity.ok(line);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<Void> deleteLine(@PathVariable Long id) {
        productionLineService.deleteProductionLine(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<Boolean> checkCodeExists(@PathVariable String code) {
        boolean exists = productionLineService.existsByCode(code);
        return ResponseEntity.ok(exists);
    }
    @GetMapping("/phase/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<List<ProductionLineResponseDTO>> getLinesByPhase(
            @PathVariable Long phaseId) {
        return ResponseEntity.ok(productionLineService.getLinesByPhase(phaseId));
    }

    @GetMapping("/secteur/{secteurId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT', 'TECH_VAL', 'TECH_PREP')")
    public ResponseEntity<List<ProductionLineResponseDTO>> getLinesBySecteur(
            @PathVariable Long secteurId) {
        return ResponseEntity.ok(productionLineService.getLinesBySecteur(secteurId));
    }
}
