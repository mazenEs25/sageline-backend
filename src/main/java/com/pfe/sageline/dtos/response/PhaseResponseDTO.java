package com.pfe.sageline.dtos.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhaseResponseDTO {

    private Long id;
    private String code;
    private String name;
    private Long secteurId;
    private String secteurCode;
    private String secteurName;
    private Integer orderIndex;
    private Boolean active;
    private int lineCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}