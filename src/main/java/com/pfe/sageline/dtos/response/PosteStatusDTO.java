package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.TicketStatus;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row in the per-poste status panel of a line-level ticket.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosteStatusDTO {

    private Long id;
    private Long validationId;

    // Zone (poste) info
    private Long zoneId;
    private String zoneName;
    private String posteType;
    private Integer orderInLine;

    // Current state
    private TicketStatus status;

    // Who closed this poste
    private Long validatedById;
    private String validatedByUsername;
    private LocalDateTime validatedAt;

    private String notes;

    // Per-poste measurement counters (2026-04 line-ticket model).
    // Populated by the mapper from ValidationResult rows attached to this poste.
    @Builder.Default
    private Long resultsCount = 0L;
    @Builder.Default
    private Long nonConformCount = 0L;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
