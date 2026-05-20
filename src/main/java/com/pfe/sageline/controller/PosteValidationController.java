package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.service.ValidationMeasureService;
import com.pfe.sageline.service.workflow.WorkflowReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only per-poste endpoints used by the Phase D frontend rewrite.
 *
 * <p>Each {@code ValidationPosteStatus} on a line ticket now owns its own measures
 * (via the {@code poste_status_id} FK added in V5.0). The frontend addresses each
 * poste by {@code zoneId} — same convention as the existing
 * {@code PATCH /api/validations/{id}/postes/{zoneId}/complete} on
 * {@link ValidationController}.</p>
 *
 * <p>Writes for a poste-scoped measure go through the existing ticket-level
 * {@code POST /api/validations/{id}/measures} — the service resolves the right
 * {@code posteStatus} from the catalog template's posteType or by walking the
 * line's postes.</p>
 */
@RestController
@RequestMapping("/api/validations/{validationId}/postes/{zoneId}")
@RequiredArgsConstructor
public class PosteValidationController {

    private final ValidationMeasureService measureService;
    private final WorkflowReadinessService readinessService;

    /**
     * All measures attached to this specific poste of the line.
     * Returns an empty list (not 404) when the poste has no measures yet.
     */
    @GetMapping("/measures")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ValidationMeasureResponse>> listMeasures(
            @PathVariable Long validationId,
            @PathVariable Long zoneId) {
        return ResponseEntity.ok(measureService.listByPoste(validationId, zoneId));
    }

    /**
     * Per-poste readiness — drives the Clôturer button on the frontend.
     *
     * <p>Same {@link WorkflowReadinessDTO} shape as the ticket-level
     * {@code /api/validations/{id}/readiness} so the frontend can reuse the same
     * model and the {@code WorkflowReadinessBar} component.</p>
     *
     * <p>{@code canTransition = true} here means "this poste can be closed":
     * every mandatory measure attributed to this poste must have a status
     * other than NOT_EXECUTED. OUT_OF_RANGE measures are reported but do <b>not</b>
     * block closure.</p>
     */
    @GetMapping("/readiness")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkflowReadinessDTO> readiness(
            @PathVariable Long validationId,
            @PathVariable Long zoneId) {
        return ResponseEntity.ok(readinessService.computePosteReadiness(validationId, zoneId));
    }
}
