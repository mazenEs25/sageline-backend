package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.request.ValidationAssignmentRequestDTO;
import com.pfe.sageline.dtos.response.ValidationAssignmentResponseDTO;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.AssignmentRole;
import org.springframework.stereotype.Component;

@Component
public class ValidationAssignmentMapper {

    public ValidationAssignment toEntity(
            ValidationAssignmentRequestDTO dto,
            Validation validation,
            User user,
            ValidationZone zone) {
        return ValidationAssignment.builder()
                .validation(validation)
                .user(user)
                .assignmentRole(dto.getAssignmentRole())
                .zone(zone)
                .notes(dto.getNotes())
                .build();
    }

    public ValidationAssignmentResponseDTO toResponseDTO(ValidationAssignment entity) {
        return ValidationAssignmentResponseDTO.builder()
                .id(entity.getId())
                .validationId(entity.getValidation() != null ?
                        entity.getValidation().getId() : null)
                .ticketCode(entity.getValidation() != null ?
                        entity.getValidation().getTicketCode() : null)
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .username(entity.getUser() != null ? entity.getUser().getUsername() : null)
                .userRole(entity.getUser() != null ?
                        entity.getUser().getRole().name() : null)
                .assignmentRole(entity.getAssignmentRole())
                .zoneId(entity.getZone() != null ? entity.getZone().getId() : null)
                .zoneName(entity.getZone() != null ? entity.getZone().getName() : null)
                .status(entity.getStatus())
                .assignedAt(entity.getAssignedAt())
                .completedAt(entity.getCompletedAt())
                .notes(entity.getNotes())
                .build();
    }
}