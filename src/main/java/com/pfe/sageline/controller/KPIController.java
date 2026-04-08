package com.pfe.sageline.controller;


import com.pfe.sageline.dtos.KPIResponse;
import com.pfe.sageline.service.KPIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kpis")
@RequiredArgsConstructor
@Tag(name = "KPI Management", description = "Endpoints pour la consultation des KPIs et statistiques")
@CrossOrigin(origins = "*")
public class KPIController {

    private final KPIService kpiService;

    @GetMapping("/line/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les KPIs d'une ligne de production")
    public ResponseEntity<List<KPIResponse>> getKPIsByLine(@PathVariable Long lineId) {
        List<KPIResponse> kpis = kpiService.getKPIsByLine(lineId);
        return ResponseEntity.ok(kpis);
    }

    @GetMapping("/line/{lineId}/latest")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les derniers KPIs d'une ligne")
    public ResponseEntity<List<KPIResponse>> getLatestKPIsByLine(@PathVariable Long lineId) {
        List<KPIResponse> kpis = kpiService.getLatestKPIsByLine(lineId);
        return ResponseEntity.ok(kpis);
    }

    @GetMapping("/line/{lineId}/date-range")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Récupérer les KPIs d'une ligne sur une période")
    public ResponseEntity<List<KPIResponse>> getKPIsByLineAndDateRange(
            @PathVariable Long lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<KPIResponse> kpis = kpiService.getKPIsByLineAndDateRange(lineId, start, end);
        return ResponseEntity.ok(kpis);
    }

    @GetMapping("/line/{lineId}/conformity-rate")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Calculer le taux de conformité d'une ligne")
    public ResponseEntity<Double> calculateConformityRate(@PathVariable Long lineId) {
        Double rate = kpiService.calculateConformityRate(lineId);
        return ResponseEntity.ok(rate);
    }

    @GetMapping("/line/{lineId}/conformity-rate/period")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Calculer le taux de conformité d'une ligne sur une période")
    public ResponseEntity<Double> calculateConformityRateForPeriod(
            @PathVariable Long lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Double rate = kpiService.calculateConformityRateForPeriod(lineId, start, end);
        return ResponseEntity.ok(rate);
    }

    @GetMapping("/line/{lineId}/validation-count")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Compter les validations d'une ligne")
    public ResponseEntity<Map<String, Long>> countValidationsByLine(@PathVariable Long lineId) {
        Map<String, Long> counts = kpiService.countValidationsByLine(lineId);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/line/{lineId}/validation-count/period")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Compter les validations d'une ligne sur une période")
    public ResponseEntity<Map<String, Long>> countValidationsByLineForPeriod(
            @PathVariable Long lineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Map<String, Long> counts = kpiService.countValidationsByLineForPeriod(lineId, start, end);
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/dashboard/global")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Dashboard global avec tous les KPIs principaux")
    public ResponseEntity<Map<String, Object>> getGlobalDashboard() {
        Map<String, Object> dashboard = kpiService.getGlobalDashboard();
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/dashboard/line/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    @Operation(summary = "Dashboard pour une ligne spécifique")
    public ResponseEntity<Map<String, Object>> getLineDashboard(@PathVariable Long lineId) {
        Map<String, Object> dashboard = kpiService.getLineDashboard(lineId);
        return ResponseEntity.ok(dashboard);
    }

    @PostMapping("/calculate/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Recalculer tous les KPIs d'une ligne")
    public ResponseEntity<List<KPIResponse>> recalculateKPIs(@PathVariable Long lineId) {
        List<KPIResponse> kpis = kpiService.recalculateKPIsForLine(lineId);
        return ResponseEntity.ok(kpis);
    }
}
