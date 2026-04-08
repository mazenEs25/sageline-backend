package com.pfe.sageline.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequestDTO {
    
    @NotNull(message = "Validation zone ID is required")
    private Long validationZoneId;
    
    private String comments;
}
