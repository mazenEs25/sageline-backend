package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.ValidationAssignmentBatchRequestDTO;
import com.pfe.sageline.dtos.request.ValidationAssignmentRequestDTO;
import com.pfe.sageline.dtos.response.ValidationAssignmentResponseDTO;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.entity.ValidationAssignment;
import com.pfe.sageline.entity.ValidationZone;
import com.pfe.sageline.enums.AssignmentRole;
import com.pfe.sageline.enums.AssignmentStatus;
import com.pfe.sageline.enums.Role;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.ValidationAssignmentMapper;
import com.pfe.sageline.repository.UserRepository;
import com.pfe.sageline.repository.ValidationAssignmentRepository;
import com.pfe.sageline.repository.ValidationRepository;
import com.pfe.sageline.repository.ValidationZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ValidationAssignmentService {

    private final ValidationAssignmentRepository assignmentRepository;
    private final ValidationRepository validationRepository;
    private final UserRepository userRepository;
    private final ValidationZoneRepository zoneRepository;
    private final ValidationAssignmentMapper assignmentMapper;
    private final MessagingEventService messagingEventService;

    @Transactional(readOnly = true)
    public List<ValidationAssignmentResponseDTO> findByValidationId(Long validationId) {
        return assignmentRepository.findByValidationIdWithDetails(validationId).stream()
                .map(assignmentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationAssignmentResponseDTO> findByUserId(Long userId) {
        return assignmentRepository.findByUserId(userId).stream()
                .map(assignmentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationAssignmentResponseDTO> findActiveByUserId(Long userId) {
        return assignmentRepository.findActiveAssignmentsByUserId(userId).stream()
                .map(assignmentMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public ValidationAssignmentResponseDTO assign(ValidationAssignmentRequestDTO dto) {
        Validation validation = validationRepository.findById(dto.getValidationId())
                .orElseThrow(() -> new ResourceNotFoundException("Validation", "id", dto.getValidationId()));

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getUserId()));

        ValidationZone zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("ValidationZone", "id", dto.getZoneId()));

        verifyRoleCompatibility(user, dto.getAssignmentRole());

        if (assignmentRepository.existsByValidationIdAndUserIdAndAssignmentRole(
                dto.getValidationId(), dto.getUserId(), dto.getAssignmentRole())) {
            throw new ValidationException("Cet utilisateur est déjà affecté à ce ticket avec ce rôle");
        }

        ValidationAssignment assignment = ValidationAssignment.builder()
                .validation(validation)
                .user(user)
                .assignmentRole(dto.getAssignmentRole())
                .zone(zone)
                .status(AssignmentStatus.ASSIGNE)
                .notes(dto.getNotes())
                .build();

        assignment = assignmentRepository.save(assignment);

        messagingEventService.onTicketAssignment(assignment);

        return assignmentMapper.toResponseDTO(assignment);
    }

    public List<ValidationAssignmentResponseDTO> assignBatch(ValidationAssignmentBatchRequestDTO batch) {
        List<ValidationAssignmentResponseDTO> results = new ArrayList<>();
        for (ValidationAssignmentRequestDTO dto : batch.getAssignments()) {
            results.add(assign(dto));
        }
        return results;
    }

    public ValidationAssignmentResponseDTO updateStatus(Long assignmentId, AssignmentStatus status) {
        ValidationAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationAssignment", "id", assignmentId));

        assignment.setStatus(status);
        if (status == AssignmentStatus.TERMINE) {
            assignment.setCompletedAt(LocalDateTime.now());
        }
        assignment = assignmentRepository.save(assignment);
        return assignmentMapper.toResponseDTO(assignment);
    }

    public void removeAssignment(Long assignmentId) {
        ValidationAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationAssignment", "id", assignmentId));
        assignmentRepository.delete(assignment);
    }

    private void verifyRoleCompatibility(User user, AssignmentRole assignmentRole) {
        Role userRole = user.getRole();
        if (userRole == null) {
            throw new ValidationException("L'utilisateur n'a pas de rôle défini");
        }
        if (assignmentRole == AssignmentRole.TECH_VALIDATION && userRole != Role.TECH_VAL) {
            throw new ValidationException("Seul un utilisateur TECH_VAL peut être affecté au rôle TECH_VALIDATION");
        }
        if (assignmentRole == AssignmentRole.TECH_PREPARATION && userRole != Role.TECH_PREP) {
            throw new ValidationException("Seul un utilisateur TECH_PREP peut être affecté au rôle TECH_PREPARATION");
        }
    }
}
