package com.pfe.sageline.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionLineResponseDTO {
    
    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private Integer zonesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
