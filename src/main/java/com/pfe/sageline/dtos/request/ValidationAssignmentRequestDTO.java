package com.pfe.sageline.dtos.request;

import com.pfe.sageline.enums.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationAssignmentRequestDTO {

    @NotNull(message = "La validation est obligatoire")
    private Long validationId;

    @NotNull(message = "L'utilisateur est obligatoire")
    private Long userId;

    @NotNull(message = "Le rôle d'affectation est obligatoire")
    private AssignmentRole assignmentRole;

    @NotNull(message = "La zone est obligatoire")
    private Long zoneId;

    private String notes;
}