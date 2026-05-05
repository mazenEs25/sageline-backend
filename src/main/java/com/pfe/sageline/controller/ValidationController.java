package com.pfe.sageline.controller;

import com.pfe.sageline.dtos.request.*;
import com.pfe.sageline.dtos.response.PrepValidationRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResponseDTO;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.service.ValidationService;
import com.pfe.sageline.Config.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/validations")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationService validationService;
    private final SecurityUtils securityUtils;

    // ========================
    // LIST & SEARCH
    // ========================

    @GetMapping
    public ResponseEntity<List<ValidationResponseDTO>> getAllValidations() {
        return ResponseEntity.ok(validationService.getAllValidations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ValidationResponseDTO> getValidationById(@PathVariable Long id) {
        return ResponseEntity.ok(validationService.getValidationById(id));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByStatus(
            @PathVariable TicketStatus status) {
        return ResponseEntity.ok(validationService.getValidationsByStatus(status));
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByZone(
            @PathVariable Long zoneId) {
        return ResponseEntity.ok(validationService.getValidationsByZone(zoneId));
    }

    @GetMapping("/line/{lineId}")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsByLine(
            @PathVariable Long lineId) {
        return ResponseEntity.ok(validationService.getValidationsByLine(lineId));
    }

    @GetMapping("/secteur/{secteurId}")
    public ResponseEntity<List<ValidationResponseDTO>> getValidationsBySecteur(
            @PathVariable Long secteurId) {
        return ResponseEntity.ok(validationService.getValidationsBySecteur(secteurId));
    }

    @GetMapping("/my-tickets")
    public ResponseEntity<List<ValidationResponseDTO>> getMyTickets() {
        Long userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(validationService.getMyTickets(userId));
    }

    @GetMapping("/week/{date}")
    public ResponseEntity<List<ValidationResponseDTO>> getTicketsByWeek(
            @PathVariable LocalDate date) {
        return ResponseEntity.ok(validationService.getTicketsByWeek(date));
    }

    // ========================
    // TICKET CREATION
    // ========================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ValidationResponseDTO> createTicket(
            @Valid @RequestBody TicketCreateRequestDTO dto) {
        Long createdByUserId = securityUtils.getCurrentUserId();
        return new ResponseEntity<>(
                validationService.createTicket(dto, createdByUserId),
                HttpStatus.CREATED);
    }

    @PostMapping("/plan-week")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<List<ValidationResponseDTO>> planWeek(
            @Valid @RequestBody TicketWeekPlanRequestDTO dto) {
        Long createdByUserId = securityUtils.getCurrentUserId();
        return new ResponseEntity<>(
                validationService.planWeek(dto, createdByUserId),
                HttpStatus.CREATED);
    }

    // ========================
    // TICKET WORKFLOW TRANSITIONS
    // ========================

    @PatchMapping("/{id}/start-prep")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'TECH_PREP')")
    public ResponseEntity<ValidationResponseDTO> startPrep(@PathVariable Long id) {
        return ResponseEntity.ok(validationService.startPrep(id));
    }

    @PatchMapping("/{id}/validate-prep")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'TECH_PREP')")
    public ResponseEntity<ValidationResponseDTO> validatePrep(
            @PathVariable Long id,
            @RequestBody PrepValidationRequestDTO dto) {
        Long techPrepUserId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(validationService.validatePrep(id, dto, techPrepUserId));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'TECH_VAL')")
    public ResponseEntity<ValidationResponseDTO> startValidation(@PathVariable Long id) {
        return ResponseEntity.ok(validationService.startValidation(id));
    }

    @PatchMapping("/{id}/submit-review")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'TECH_VAL')")
    public ResponseEntity<ValidationResponseDTO> submitForReview(@PathVariable Long id) {
        return ResponseEntity.ok(validationService.submitForReview(id));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR', 'EXPERT')")
    public ResponseEntity<ValidationResponseDTO> closeTicket(
            @PathVariable Long id,
            @RequestBody TicketCloseRequestDTO dto) {
        return ResponseEntity.ok(validationService.closeTicket(id, dto));
    }

    /**
     * Mark a single poste of a line-ticket as CONFORME or NON_CONFORME.
     * Added 2026-04 with the line-ticket model — lets different techs close
     * different postes of the same ticket independently.
     */
    @PatchMapping("/{id}/postes/{zoneId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'TECH_VAL', 'TECH_PREP', 'CHEF_SECTEUR', 'EXPERT')")
    public ResponseEntity<ValidationResponseDTO> markPosteDone(
            @PathVariable Long id,
            @PathVariable Long zoneId,
            @Valid @RequestBody PosteCompleteRequestDTO dto) {
        Long actingUserId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(validationService.markPosteDone(
                id, zoneId, dto.getFinalStatus(), dto.getNotes(), actingUserId));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<ValidationResponseDTO> cancelTicket(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(validationService.cancelTicket(id, reason != null ? reason : "Annulé"));
    }

    // ========================
    // DELETE
    // ========================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    public ResponseEntity<Void> deleteValidation(@PathVariable Long id) {
        validationService.deleteValidation(id);
        return ResponseEntity.noContent().build();
    }
}