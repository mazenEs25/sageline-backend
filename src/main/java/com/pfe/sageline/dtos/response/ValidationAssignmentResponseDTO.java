package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.AssignmentRole;
import com.pfe.sageline.enums.AssignmentStatus;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationAssignmentResponseDTO {

    private Long id;
    private Long validationId;
    private String ticketCode;
    private Long userId;
    private String username;
    private String userRole;
    private AssignmentRole assignmentRole;
    private Long zoneId;
    private String zoneName;
    private AssignmentStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
    private String notes;
}