package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultRequestDTO {
    
    @NotBlank(message = "Parameter is required")
    private String parameter;
    
    @NotNull(message = "Measured value is required")
    private Double measuredValue;
    
    @NotNull(message = "Expected value is required")
    private Double expectedValue;
    
    @NotNull(message = "Validation ID is required")
    private Long validationId;

    /**
     * Per-poste link (2026-04 line-ticket model).
     * Optional: if provided, the result is attached to a specific poste of the line.
     * The backend validates that this zone belongs to the ticket's production line.
     */
    private Long zoneId;
}
