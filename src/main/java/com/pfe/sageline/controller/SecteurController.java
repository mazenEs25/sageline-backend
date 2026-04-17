package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.SecteurRequestDTO;
import com.pfe.sageline.dtos.response.SecteurResponseDTO;
import com.pfe.sageline.service.SecteurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/secteurs")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class SecteurController {

    private final SecteurService secteurService;

    @GetMapping
    public ResponseEntity<List<SecteurResponseDTO>> getAllSecteurs() {
        return ResponseEntity.ok(secteurService.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<SecteurResponseDTO>> getActiveSecteurs() {
        return ResponseEntity.ok(secteurService.findAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecteurResponseDTO> getSecteurById(@PathVariable Long id) {
        return ResponseEntity.ok(secteurService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<SecteurResponseDTO> createSecteur(
            @Valid @RequestBody SecteurRequestDTO dto) {
        return new ResponseEntity<>(secteurService.create(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<SecteurResponseDTO> updateSecteur(
            @PathVariable Long id,
            @Valid @RequestBody SecteurRequestDTO dto) {
        return ResponseEntity.ok(secteurService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    public ResponseEntity<Void> deleteSecteur(@PathVariable Long id) {
        secteurService.delete(id);
        return ResponseEntity.noContent().build();
    }
}