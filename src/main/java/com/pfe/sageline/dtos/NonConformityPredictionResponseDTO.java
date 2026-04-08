package com.pfe.sageline.dtos;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NonConformityPredictionResponseDTO {

    private Long id;
    private Double riskScore;
    private String riskLevel;
    private Double confidence;
    private LocalDateTime predictedAt;
    private Long validationId;
}