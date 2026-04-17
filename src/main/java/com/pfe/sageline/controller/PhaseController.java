package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.PhaseRequestDTO;
import com.pfe.sageline.dtos.response.PhaseResponseDTO;
import com.pfe.sageline.service.PhaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/phases")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class PhaseController {

    private final PhaseService phaseService;

    @GetMapping
    public ResponseEntity<List<PhaseResponseDTO>> getAllPhases() {
        return ResponseEntity.ok(phaseService.findAll());
    }

    @GetMapping("/secteur/{secteurId}")
    public ResponseEntity<List<PhaseResponseDTO>> getPhasesBySecteur(
            @PathVariable Long secteurId) {
        return ResponseEntity.ok(phaseService.findBySecteurId(secteurId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhaseResponseDTO> getPhaseById(@PathVariable Long id) {
        return ResponseEntity.ok(phaseService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<PhaseResponseDTO> createPhase(
            @Valid @RequestBody PhaseRequestDTO dto) {
        return new ResponseEntity<>(phaseService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<PhaseResponseDTO> updatePhase(
            @PathVariable Long id,
            @Valid @RequestBody PhaseRequestDTO dto) {
        return ResponseEntity.ok(phaseService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<Void> deletePhase(@PathVariable Long id) {
        phaseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}