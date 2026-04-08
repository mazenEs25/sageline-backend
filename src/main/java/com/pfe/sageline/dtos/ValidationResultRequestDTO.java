package com.pfe.sageline.dtos;

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
}
