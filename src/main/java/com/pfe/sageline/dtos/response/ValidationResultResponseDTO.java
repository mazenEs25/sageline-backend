package com.pfe.sageline.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultResponseDTO {
    
    private Long id;
    private String parameter;
    private Double measuredValue;
    private Double expectedValue;
    private Boolean conform;
    private LocalDateTime createdAt;
}
