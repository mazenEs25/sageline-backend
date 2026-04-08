package com.pfe.sageline.dtos;

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
}
