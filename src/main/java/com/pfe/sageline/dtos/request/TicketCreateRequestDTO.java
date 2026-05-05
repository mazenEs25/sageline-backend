package com.pfe.sageline.dtos.request;

import com.pfe.sageline.enums.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCreateRequestDTO {

    // ===== NEW (2026-04): Line-level ticket =====
    // Since the Sagemcom supervisor mandate, one ticket covers an entire production
    // line. The line id is the canonical identifier; the zone id below is kept only
    // for backward compatibility with older clients that still cascade down to poste.
    @NotNull(message = "La ligne de production est obligatoire")
    private Long productionLineId;

    /**
     * Optional "primary" poste of the line. When omitted, the service picks the
     * first {@link com.pfe.sageline.entity.ValidationZone} of the line (ordered by
     * {@code orderInLine}) so legacy code paths (AI prediction, zone-scoped KPIs,
     * etc.) keep working while we migrate the call-sites.
     */
    private Long validationZoneId;

    /**
     * Optional subset of the line's postes to include in the ticket. When null
     * or empty, every poste of the line is covered (default — unchanged
     * behaviour from the 2026-04 refactor). When non-empty, every listed id
     * must belong to the selected {@code productionLineId} — the service
     * rejects any id that doesn't, and the final included set must be
     * non-empty. The excluded postes simply don't get a
     * {@link com.pfe.sageline.entity.ValidationPosteStatus} sub-row.
     */
    private List<Long> includedZoneIds;

    private LocalDate plannedDate;

    private LocalDate plannedWeekStart;

    private LocalDate plannedWeekEnd;

    private Priority priority = Priority.NORMALE;

    private String comments;

    // Assignments to create with the ticket
    @Valid
    private List<TicketAssignmentDTO> assignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TicketAssignmentDTO {
        @NotNull
        private Long userId;
        @NotNull
        private String assignmentRole; // TECH_VALIDATION or TECH_PREPARATION
        private Long zoneId; // If different from main zone
    }
}