package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.service.workflow.WorkflowReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/validations")
@RequiredArgsConstructor
public class WorkflowReadinessController {

    private final WorkflowReadinessService readinessService;

    @GetMapping("/{id}/readiness")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkflowReadinessDTO> readiness(
            @PathVariable Long id,
            @RequestParam(value = "targetStatus", required = false) TicketStatus targetStatus) {
        return ResponseEntity.ok(readinessService.computeReadiness(id, targetStatus));
    }
}
