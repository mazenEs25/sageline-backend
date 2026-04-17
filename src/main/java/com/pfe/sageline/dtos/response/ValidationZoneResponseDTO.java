package com.pfe.sageline.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationZoneResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private Long productionLineId;
    private String productionLineName;
    private Integer validationsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String posteType;
    private Integer orderInLine;
    private Boolean requiresToolCheck;

    // Also add hierarchy info:
    private Long lineId;
    private String lineCode;
    private Long phaseId;
    private String phaseCode;
    private Long secteurId;
    private String secteurCode;
}
