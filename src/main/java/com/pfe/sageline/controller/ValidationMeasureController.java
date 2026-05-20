package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.BatchCreateMeasureRequest;
import com.pfe.sageline.dtos.request.BatchUpdateMeasureRequest;
import com.pfe.sageline.dtos.request.CreateMeasureRequest;
import com.pfe.sageline.dtos.request.UpdateMeasureRequest;
import com.pfe.sageline.dtos.response.BatchValidationMeasureResponse;
import com.pfe.sageline.dtos.response.ValidationMeasureResponse;
import com.pfe.sageline.service.ValidationMeasureService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ticket-level measure endpoints.
 *
 * <p><b>Phase E status:</b> the mutation endpoints below ({@code POST /measures},
 * {@code POST /measures/batch}, {@code POST /measures/from-template}, {@code POST
 * /measures/from-template/{templateId}}) are <b>deprecated</b> in favour of the
 * per-poste endpoints on {@link PosteValidationController}. They still work
 * unchanged, but every response carries:
 * <ul>
 *   <li>HTTP header {@code Deprecation: true} (RFC 8594 draft) so HTTP clients can
 *       detect deprecation programmatically;</li>
 *   <li>a {@code Sunset} header pointing at the per-poste replacement;</li>
 *   <li>a server-log {@code WARN} naming the caller's {@code User-Agent} and
 *       {@code X-Requested-By} header for an inventory of remaining legacy callers.</li>
 * </ul>
 * The read endpoints ({@code GET /measures}) are not deprecated — they remain the
 * ticket-wide aggregate view.</p>
 */
@RestController
@RequestMapping("/api/validations/{validationId}/measures")
@RequiredArgsConstructor
@Slf4j
public class ValidationMeasureController {

    private final ValidationMeasureService service;

    /** Stamps every deprecated response with the standard headers + a server-log warning. */
    private static <T> ResponseEntity<T> withDeprecation(
            ResponseEntity<T> base, HttpServletRequest req, String replacement) {
        log.warn("[deprecated-call] {} {} ua=\"{}\" requested-by=\"{}\" — use {}",
                req.getMethod(), req.getRequestURI(),
                req.getHeader("User-Agent"),
                req.getHeader("X-Requested-By"),
                replacement);
        return ResponseEntity.status(base.getStatusCode())
                .headers(h -> {
                    h.set("Deprecation", "true");
                    h.set("Sunset", replacement);
                    if (base.getHeaders() != null) base.getHeaders().forEach(h::put);
                })
                .body(base.getBody());
    }

    @GetMapping
    public List<ValidationMeasureResponse> list(@PathVariable Long validationId) {
        return service.listByValidation(validationId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<ValidationMeasureResponse> create(
            @PathVariable Long validationId,
            @Valid @RequestBody CreateMeasureRequest req,
            HttpServletRequest httpReq) {
        ResponseEntity<ValidationMeasureResponse> resp = ResponseEntity
                .status(HttpStatus.CREATED).body(service.create(validationId, req));
        return withDeprecation(resp, httpReq,
                "POST /api/validations/{id}/postes/{zoneId}/measures (Phase B)");
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<List<ValidationMeasureResponse>> batchCreate(
            @PathVariable Long validationId,
            @Valid @RequestBody BatchCreateMeasureRequest req,
            HttpServletRequest httpReq) {
        ResponseEntity<List<ValidationMeasureResponse>> resp = ResponseEntity
                .status(HttpStatus.CREATED).body(service.batchCreate(validationId, req));
        return withDeprecation(resp, httpReq,
                "POST /api/validations/{id}/postes/{zoneId}/measures/batch (Phase B)");
    }

    /**
     * Bulk-update measuredValue on existing rows. Returns a structured
     * {@link BatchValidationMeasureResponse} with per-row status so the UI can
     * highlight failing rows without rejecting the whole batch.
     *
     * <p>Frontend caller: {@code ValidationMeasureService.updateBatch} →
     * {@code MeasurePanel} "Édition groupée → Enregistrer tout".</p>
     */
    @PutMapping("/batch")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<BatchValidationMeasureResponse> batchUpdate(
            @PathVariable Long validationId,
            @Valid @RequestBody BatchUpdateMeasureRequest req) {
        return ResponseEntity.ok(service.batchUpdate(validationId, req));
    }

    @PostMapping("/from-template")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<List<ValidationMeasureResponse>> instantiateFromCatalog(
            @PathVariable Long validationId,
            HttpServletRequest httpReq) {
        // The seeder is now line-wide (Phase A) — kept here for back-compat with the
        // ticket-scoped UI surface; the per-poste seeder is the new canonical path.
        ResponseEntity<List<ValidationMeasureResponse>> resp =
                ResponseEntity.ok(service.instantiateFromCatalog(validationId));
        return withDeprecation(resp, httpReq,
                "POST /api/validations/{id}/postes/{zoneId}/measures/from-template (Phase B)");
    }

    /**
     * Per-template instantiation. Idempotent. Returns the freshly created
     * (or pre-existing) NOT_EXECUTED measure linked to the given catalog template.
     */
    @PostMapping("/from-template/{templateId}")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ResponseEntity<ValidationMeasureResponse> instantiateOneFromCatalog(
            @PathVariable Long validationId,
            @PathVariable Long templateId,
            HttpServletRequest httpReq) {
        ResponseEntity<ValidationMeasureResponse> resp = ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.instantiateOneFromCatalog(validationId, templateId));
        return withDeprecation(resp, httpReq,
                "POST /api/validations/{id}/postes/{zoneId}/measures/from-template (Phase B)");
    }

    @PutMapping("/{measureId}")
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public ValidationMeasureResponse update(
            @PathVariable Long validationId,
            @PathVariable Long measureId,
            @Valid @RequestBody UpdateMeasureRequest req) {
        return service.update(validationId, measureId, req);
    }

    @DeleteMapping("/{measureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TECH_VAL','CHEF_SECTEUR','ADMIN_IT')")
    public void delete(@PathVariable Long validationId, @PathVariable Long measureId) {
        service.delete(validationId, measureId);
    }
}
