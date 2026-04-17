package com.pfe.sageline.dtos;

import com.pfe.sageline.enums.ToolStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
@Data
@NoArgsConstructor
@AllArgsConstructor
public  class ToolScoreDTO {
    private Long id;
    private String name;
    private String description;
    private ToolStatus status;
    private double score;
    private Map<String, Double> scoreBreakdown;
    private Double successRate;
    private Integer usageCount;
    private LocalDateTime lastMaintenance;
}
