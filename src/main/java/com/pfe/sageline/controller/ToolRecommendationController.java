package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.ToolScoreDTO;
import com.pfe.sageline.entity.ToolRecommendation;
import com.pfe.sageline.service.ToolRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Tag(name = "Tool Recommendation", description = "AI Model 2 — Recommandation d'outillage")
@CrossOrigin(origins = "*")
public class ToolRecommendationController {

    private final ToolRecommendationService toolService;

    @GetMapping("/recommend")
    @Operation(summary = "Obtenir les recommandations d'outillage pour une zone/ligne")
    public ResponseEntity<List<ToolScoreDTO>> getRecommendations(
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long lineId) {
        List<ToolScoreDTO> recommendations =
                toolService.recommendTools(zoneId, lineId);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping
    @Operation(summary = "Lister tous les outils")
    public ResponseEntity<List<ToolRecommendation>> getAllTools() {
        return ResponseEntity.ok(toolService.getAllTools());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un outil par ID")
    public ResponseEntity<ToolRecommendation> getToolById(@PathVariable Long id) {
        return ResponseEntity.ok(toolService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Créer un outil")
    public ResponseEntity<ToolRecommendation> createTool(@RequestBody ToolRecommendation tool) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toolService.createTool(tool));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Modifier un outil")
    public ResponseEntity<ToolRecommendation> updateTool(
            @PathVariable Long id,
            @RequestBody ToolRecommendation tool) {
        return ResponseEntity.ok(toolService.updateTool(id, tool));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Supprimer un outil")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        toolService.deleteTool(id);
        return ResponseEntity.noContent().build();
    }
}