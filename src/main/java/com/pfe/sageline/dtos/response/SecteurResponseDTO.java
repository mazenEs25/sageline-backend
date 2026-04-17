package com.pfe.sageline.dtos.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecteurResponseDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private int phaseCount;
    private int lineCount;
    private int userCount;
    private List<PhaseResponseDTO> phases; // Only populated on detail view
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}