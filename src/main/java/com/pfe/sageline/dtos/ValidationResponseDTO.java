package com.pfe.sageline.dtos;

import com.pfe.sageline.entity.ValidationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponseDTO {
    
    private Long id;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ValidationStatus status;
    private String comments;
    private Long validationZoneId;
    private String validationZoneName;
    private String productionLineName;
    private List<ValidationResultResponseDTO> results;
    private Double riskScore;
    private String riskLevel;
}
