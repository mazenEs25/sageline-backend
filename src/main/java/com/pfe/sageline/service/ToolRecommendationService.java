package com.pfe.sageline.service;

import com.pfe.sageline.dtos.ToolScoreDTO;
import com.pfe.sageline.enums.ToolStatus;
import com.pfe.sageline.entity.ToolRecommendation;
import com.pfe.sageline.repository.ToolRecommendationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ToolRecommendationService {

    private final ToolRecommendationRepository toolRepository;

    public ToolRecommendationService(ToolRecommendationRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    /**
     * MODEL 2: Recommend tools for a given zone and line.
     * Uses a weighted scoring algorithm based on:
     * - Success rate (40%)
     * - Availability status (20%)
     * - Maintenance recency (15%)
     * - Usage experience (15%)
     * - Zone/Line compatibility (10%)
     */
    public List<ToolScoreDTO> recommendTools(Long zoneId, Long lineId) {
        List<ToolRecommendation> allTools = toolRepository.findByStatus(ToolStatus.DISPONIBLE);

        if (allTools.isEmpty()) {
            // Fallback: return all tools sorted by success rate
            allTools = toolRepository.findAll();
        }

        return allTools.stream()
                .map(tool -> scoreTool(tool, zoneId, lineId))
                .sorted(Comparator.comparingDouble(ToolScoreDTO::getScore).reversed())
                .limit(5) // Top 5 recommendations
                .collect(Collectors.toList());
    }

    private ToolScoreDTO scoreTool(ToolRecommendation tool, Long zoneId, Long lineId) {
        double score = 0.0;
        Map<String, Double> breakdown = new LinkedHashMap<>();

        // 1. Success Rate Score (40%) — higher is better
        double successScore = (tool.getSuccessRate() != null ? tool.getSuccessRate() : 50.0) / 100.0;
        score += successScore * 0.40;
        breakdown.put("successRate", Math.round(successScore * 100.0) / 100.0);

        // 2. Availability Score (20%)
        double availScore = 0.0;
        if (tool.getStatus() != null) {
            switch (tool.getStatus()) {
                case DISPONIBLE: availScore = 1.0; break;
                case EN_UTILISATION: availScore = 0.3; break;
                case EN_MAINTENANCE: availScore = 0.0; break;
            }
        } else {
            availScore = 0.5;
        }
        score += availScore * 0.20;
        breakdown.put("availability", availScore);

        // 3. Maintenance Recency Score (15%) — recently maintained = better
        double maintScore = 0.5; // default
        if (tool.getLastMaintenanceDate() != null) {
            long daysSinceMaint = ChronoUnit.DAYS.between(tool.getLastMaintenanceDate(), LocalDateTime.now());
            if (daysSinceMaint < 7) maintScore = 1.0;
            else if (daysSinceMaint < 30) maintScore = 0.8;
            else if (daysSinceMaint < 90) maintScore = 0.5;
            else maintScore = 0.2;
        }
        score += maintScore * 0.15;
        breakdown.put("maintenance", maintScore);

        // 4. Usage Experience Score (15%) — more usage = more reliable data
        double usageScore = 0.5;
        if (tool.getUsageCount() != null) {
            if (tool.getUsageCount() > 100) usageScore = 1.0;
            else if (tool.getUsageCount() > 50) usageScore = 0.8;
            else if (tool.getUsageCount() > 20) usageScore = 0.6;
            else if (tool.getUsageCount() > 5) usageScore = 0.4;
            else usageScore = 0.2;
        }
        score += usageScore * 0.15;
        breakdown.put("experience", usageScore);

        // 5. Compatibility Score (10%) — matches zone/line
        double compatScore = 0.5; // neutral if no compatibility data
        if (tool.getCompatibleZones() != null && zoneId != null) {
            compatScore = tool.getCompatibleZones().contains(zoneId.toString()) ? 1.0 : 0.3;
        }
        if (tool.getCompatibleLines() != null && lineId != null) {
            if (tool.getCompatibleLines().contains(lineId.toString())) {
                compatScore = Math.max(compatScore, 0.8);
            }
        }
        score += compatScore * 0.10;
        breakdown.put("compatibility", compatScore);

        return new ToolScoreDTO(
                tool.getId(),
                tool.getName(),
                tool.getDescription(),
                tool.getStatus(),
                Math.round(score * 1000.0) / 1000.0, // 3 decimal places
                breakdown,
                tool.getSuccessRate(),
                tool.getUsageCount(),
                tool.getLastMaintenanceDate()
        );
    }

    // ─── CRUD ───

    public List<ToolRecommendation> getAllTools() {
        return toolRepository.findAll();
    }

    public ToolRecommendation getById(Long id) {
        return toolRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Outil non trouvé: " + id));
    }

    public ToolRecommendation createTool(ToolRecommendation tool) {
        return toolRepository.save(tool);
    }

    public ToolRecommendation updateTool(Long id, ToolRecommendation request) {
        ToolRecommendation tool = getById(id);
        if (request.getName() != null) tool.setName(request.getName());
        if (request.getDescription() != null) tool.setDescription(request.getDescription());
        if (request.getStatus() != null) tool.setStatus(request.getStatus());
        if (request.getSuccessRate() != null) tool.setSuccessRate(request.getSuccessRate());
        if (request.getUsageCount() != null) tool.setUsageCount(request.getUsageCount());
        if (request.getLastMaintenanceDate() != null) tool.setLastMaintenanceDate(request.getLastMaintenanceDate());
        if (request.getNextMaintenanceDate() != null) tool.setNextMaintenanceDate(request.getNextMaintenanceDate());
        if (request.getCompatibleZones() != null) tool.setCompatibleZones(request.getCompatibleZones());
        if (request.getCompatibleLines() != null) tool.setCompatibleLines(request.getCompatibleLines());
        return toolRepository.save(tool);
    }

    public void deleteTool(Long id) {
        toolRepository.deleteById(id);
    }





    }
