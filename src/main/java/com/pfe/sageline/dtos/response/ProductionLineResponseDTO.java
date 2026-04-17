package com.pfe.sageline.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Long phaseId;
    private String phaseCode;
    private String phaseName;
    private Long secteurId;
    private String secteurCode;
    private String secteurName;
    private Integer lineNumber;
}
