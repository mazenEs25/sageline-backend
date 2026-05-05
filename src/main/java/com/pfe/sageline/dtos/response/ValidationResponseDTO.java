package com.pfe.sageline.dtos.response;

import com.pfe.sageline.dtos.response.ValidationAssignmentResponseDTO;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.enums.Priority;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResponseDTO {

    private Long id;
    private String ticketCode;
    private TicketStatus status;
    private Priority priority;

    // Zone info
    private Long validationZoneId;
    private String zoneName;
    private String posteType;

    // Line info (through zone)
    private Long lineId;
    private String lineCode;
    private String lineName;

    // Phase info (through line)
    private Long phaseId;
    private String phaseCode;
    private String phaseName;

    // Secteur info (through phase)
    private Long secteurId;
    private String secteurCode;

    // Creator
    private Long createdById;
    private String createdByUsername;

    // Planning
    private LocalDate plannedDate;
    private LocalDate plannedWeekStart;
    private LocalDate plannedWeekEnd;

    // Dates
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    // Comments
    private String comments;
    private String prepComments;
    private String reviewComments;

    // Tool verification
    private Boolean toolsVerified;
    private LocalDateTime toolsVerifiedAt;
    private String toolsVerifiedByUsername;

    // Results summary
    private int resultsCount;
    private int conformCount;
    private int nonConformCount;
    private Double conformityRate;

    // AI Prediction
    private Double riskScore;
    private String riskLevel;
    private Double confidence;

    // Assignments
    private List<ValidationAssignmentResponseDTO> assignments;

    // ===== NEW (2026-04): Per-poste sub-statuses =====
    // One row per required poste of the line. Ordered by ValidationPosteStatus.orderInLine.
    private List<PosteStatusDTO> posteStatuses;

    // Aggregates over posteStatuses — nice for list views that don't want to
    // re-iterate the collection on the client side.
    private int posteTotal;
    private int posteDone;      // CONFORME + NON_CONFORME
    private int posteConforme;
    private int posteNonConforme;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}