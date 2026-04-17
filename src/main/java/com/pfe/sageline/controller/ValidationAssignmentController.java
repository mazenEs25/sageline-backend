package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.ValidationAssignmentBatchRequestDTO;
import com.pfe.sageline.dtos.request.ValidationAssignmentRequestDTO;
import com.pfe.sageline.dtos.response.ValidationAssignmentResponseDTO;
import com.pfe.sageline.enums.AssignmentStatus;
import com.pfe.sageline.service.ValidationAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class ValidationAssignmentController {

    private final ValidationAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ValidationAssignmentResponseDTO> createAssignment(
            @Valid @RequestBody ValidationAssignmentRequestDTO dto) {
        return new ResponseEntity<>(assignmentService.assign(dto), HttpStatus.CREATED);
    }
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<List<ValidationAssignmentResponseDTO>> createBatchAssignments(
            @Valid @RequestBody List<ValidationAssignmentRequestDTO> assignments) {
        return new ResponseEntity<>(
                assignmentService.assignBatch((ValidationAssignmentBatchRequestDTO) assignments),
                HttpStatus.CREATED);
    }

    @GetMapping("/validation/{validationId}")
    public ResponseEntity<List<ValidationAssignmentResponseDTO>> getAssignmentsByValidation(
            @PathVariable Long validationId) {
        return ResponseEntity.ok(assignmentService.findByValidationId(validationId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ValidationAssignmentResponseDTO>> getAssignmentsByUser(
            @PathVariable Long userId) {
        return ResponseEntity.ok(assignmentService.findActiveByUserId(userId));
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<List<ValidationAssignmentResponseDTO>> getActiveAssignmentsByUser(
            @PathVariable Long userId) {
        return ResponseEntity.ok(assignmentService.findActiveByUserId(userId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ValidationAssignmentResponseDTO> updateAssignmentStatus(
            @PathVariable Long id,
            @RequestParam AssignmentStatus status) {
        return ResponseEntity.ok(assignmentService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.removeAssignment(id);
        return ResponseEntity.noContent().build();
    }
}