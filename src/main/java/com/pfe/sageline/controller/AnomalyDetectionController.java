package com.pfe.sageline.controller;

import com.pfe.sageline.service.AnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
@Tag(name = "Anomaly Detection", description = "AI Model 3 — Détection d'anomalies")
@CrossOrigin(origins = "*")
public class AnomalyDetectionController {

    private final AnomalyDetectionService anomalyService;

    @GetMapping("/detect/{validationId}")
    @Operation(summary = "Détecter les anomalies pour une validation")
    public ResponseEntity<List<AnomalyDetectionService.AnomalyResult>> detectAnomalies(
            @PathVariable Long validationId) {
        return ResponseEntity.ok(anomalyService.detectAnomalies(validationId));
    }

    @GetMapping("/scan")
    @Operation(summary = "Scanner toutes les validations actives")
    public ResponseEntity<List<AnomalyDetectionService.AnomalyResult>> scanAllActive() {
        return ResponseEntity.ok(anomalyService.scanAllActive());
    }

    @GetMapping("/zone/{zoneId}/report")
    @Operation(summary = "Rapport d'anomalies pour une zone")
    public ResponseEntity<Map<String, Object>> getZoneReport(
            @PathVariable Long zoneId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(anomalyService.getZoneAnomalyReport(zoneId, days));
    }
}