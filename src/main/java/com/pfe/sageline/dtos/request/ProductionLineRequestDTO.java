package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionLineRequestDTO {
    
    @NotBlank(message = "Code is required")
    private String code;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private Boolean active = true;
    
    @NotNull(message = "La phase est obligatoire")
    private Long phaseId;

    private Integer lineNumber;
}
