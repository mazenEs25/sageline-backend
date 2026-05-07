package com.pfe.sageline.controller;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.HandoverAssignRequest;
import com.pfe.sageline.dtos.request.HandoverInitiateRequest;
import com.pfe.sageline.dtos.response.HandoverKpiResponse;
import com.pfe.sageline.dtos.response.HandoverResponse;
import com.pfe.sageline.enums.AssignmentStatus;
import com.pfe.sageline.enums.TriggerType;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.repository.ValidationAssignmentRepository;
import com.pfe.sageline.service.HandoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/handovers")
@RequiredArgsConstructor
@Tag(name = "Handover", description = "Gestion des passations de poste en fin de shift")
public class HandoverController {

    private final HandoverService handoverService;
    private final SecurityUtils securityUtils;
    private final ValidationAssignmentRepository assignmentRepository;

    /**
     * US3/US5 — Initiate a manual or forced handover on an in-progress ticket.
     * The trigger type is determined from the caller's role and their assignment status:
     *   - TECH_VAL + IS active assignee → MANUAL
     *   - CHEF_SECTEUR or ADMIN_IT → ADMIN_FORCE
     */
    @Operation(summary = "Initier une passation manuelle ou forcée")
    @PostMapping("/initiate/{validationId}")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<HandoverResponse> initiateHandover(
            @PathVariable Long validationId,
            @RequestBody(required = false) HandoverInitiateRequest req) {

        TriggerType trigger = resolveTrigger(validationId);
        return ResponseEntity.ok(handoverService.initiateHandover(validationId, req, trigger));
    }

    /**
     * US2 — TECH_VAL self-accepts a pending handover and resumes the ticket.
     */
    @Operation(summary = "Accepter une passation (auto-affectation)")
    @PostMapping("/{handoverId}/accept")
    @PreAuthorize("hasRole('TECH_VAL')")
    public ResponseEntity<HandoverResponse> acceptHandover(@PathVariable Long handoverId) {
        return ResponseEntity.ok(handoverService.acceptHandover(handoverId));
    }

    /**
     * US4 — CHEF_SECTEUR or ADMIN_IT designates a specific TECH_VAL as recipient.
     */
    @Operation(summary = "Assigner un technicien à une passation")
    @PatchMapping("/{handoverId}/assign")
    @PreAuthorize("hasAnyRole('CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<HandoverResponse> assignHandover(
            @PathVariable Long handoverId,
            @RequestBody HandoverAssignRequest req) {
        return ResponseEntity.ok(handoverService.assignHandover(handoverId, req));
    }

    /**
     * US6 — CHEF_SECTEUR or ADMIN_IT cancels a non-terminal handover.
     */
    @Operation(summary = "Annuler une passation")
    @PatchMapping("/{handoverId}/cancel")
    @PreAuthorize("hasAnyRole('CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<HandoverResponse> cancelHandover(@PathVariable Long handoverId) {
        return ResponseEntity.ok(handoverService.cancelHandover(handoverId));
    }

    /**
     * US4 — List all pending handovers (supervisor queue panel).
     */
    @Operation(summary = "Lister toutes les passations en attente")
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<List<HandoverResponse>> getPendingHandovers() {
        return ResponseEntity.ok(handoverService.getPendingHandovers());
    }

    /**
     * US7 — Full chronological handover history for a ticket.
     */
    @Operation(summary = "Historique des passations d'un ticket")
    @GetMapping("/validation/{validationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HandoverResponse>> getHandoverHistory(@PathVariable Long validationId) {
        return ResponseEntity.ok(handoverService.getHandoverHistory(validationId));
    }

    /**
     * US7 — Handover KPI snapshot for a date range.
     */
    @Operation(summary = "KPIs de passation sur une plage de dates")
    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('CHEF_SECTEUR','EXPERT','ADMIN_IT')")
    public ResponseEntity<HandoverKpiResponse> getHandoverKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(handoverService.getHandoverKpis(from, to));
    }

    /**
     * Determines trigger type from caller's role and active assignment status.
     * TECH_VAL who IS the current assignee → MANUAL.
     * CHEF_SECTEUR or ADMIN_IT → ADMIN_FORCE.
     * Otherwise → 403.
     */
    private TriggerType resolveTrigger(Long validationId) {
        Long currentUserId = securityUtils.getCurrentUserId();
        boolean isTechVal = securityUtils.hasRole("TECH_VAL");
        boolean isForce = securityUtils.hasRole("CHEF_SECTEUR") || securityUtils.hasRole("ADMIN_IT");

        if (isTechVal) {
            boolean isActiveAssignee = assignmentRepository.findByValidationId(validationId).stream()
                    .anyMatch(a -> a.getUser().getId().equals(currentUserId)
                            && a.getStatus() == AssignmentStatus.EN_COURS);
            if (isActiveAssignee) {
                return TriggerType.MANUAL;
            }
        }
        if (isForce) {
            return TriggerType.ADMIN_FORCE;
        }
        throw new ValidationException("Vous n'êtes pas autorisé à initier cette passation");
    }
}
