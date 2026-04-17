package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationZoneRequestDTO {
    
    @NotBlank(message = "Name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Production line ID is required")
    private Long productionLineId;
    private String posteType; // PosteType enum name
    private Integer orderInLine;
    private Boolean requiresToolCheck = true;
}
