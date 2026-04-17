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

    @NotNull(message = "La zone de validation est obligatoire")
    private Long validationZoneId;

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