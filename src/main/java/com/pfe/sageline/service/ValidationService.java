package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.*;
import com.pfe.sageline.dtos.response.PrepValidationRequestDTO;
import com.pfe.sageline.dtos.response.ValidationResponseDTO;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.ValidationMapper;
import com.pfe.sageline.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final ValidationRepository validationRepository;
    private final ValidationZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final ValidationAssignmentRepository assignmentRepository;
    private final ValidationMapper validationMapper;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final AIPredictionService aiPredictionService;
    private final KPIService kpiService;
    private final MessagingEventService messagingEventService;

    // ========================
    // CRUD
    // ========================

    public List<ValidationResponseDTO> getAllValidations() {
        return validationRepository.findAll().stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public ValidationResponseDTO getValidationById(Long id) {
        Validation validation = findValidationOrThrow(id);
        return validationMapper.toResponseDTO(validation);
    }

    public List<ValidationResponseDTO> getValidationsByStatus(TicketStatus status) {
        return validationRepository.findByStatus(status).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public List<ValidationResponseDTO> getValidationsByZone(Long zoneId) {
        return validationRepository.findByValidationZoneId(zoneId).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public List<ValidationResponseDTO> getValidationsByLine(Long lineId) {
        return validationRepository.findByProductionLineId(lineId).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public List<ValidationResponseDTO> getValidationsBySecteur(Long secteurId) {
        return validationRepository.findBySecteurId(secteurId).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public List<ValidationResponseDTO> getMyTickets(Long userId) {
        return validationRepository.findActiveTicketsByUserId(userId).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public List<ValidationResponseDTO> getTicketsByWeek(LocalDate date) {
        return validationRepository.findByPlannedWeek(date).stream()
                .map(validationMapper::toResponseDTO)
                .toList();
    }

    public void deleteValidation(Long id) {
        if (!validationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Validation non trouvée");
        }
        validationRepository.deleteById(id);
    }

    // ========================
    // TICKET CREATION
    // ========================

    public ValidationResponseDTO createTicket(TicketCreateRequestDTO dto, Long createdByUserId) {
        ValidationZone zone = zoneRepository.findById(dto.getValidationZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zone non trouvée"));

        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        String ticketCode = ticketCodeGenerator.generate();

        Validation validation = validationMapper.toEntity(dto, zone, createdBy, ticketCode);
        validation = validationRepository.save(validation);

        // Create assignments if provided
        if (dto.getAssignments() != null && !dto.getAssignments().isEmpty()) {
            for (TicketCreateRequestDTO.TicketAssignmentDTO assignDto : dto.getAssignments()) {
                User assignUser = userRepository.findById(assignDto.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Utilisateur non trouvé: " + assignDto.getUserId()));

                Long assignZoneId = assignDto.getZoneId() != null ?
                        assignDto.getZoneId() : dto.getValidationZoneId();
                ValidationZone assignZone = zoneRepository.findById(assignZoneId)
                        .orElseThrow(() -> new ResourceNotFoundException("Zone non trouvée"));

                ValidationAssignment assignment = ValidationAssignment.builder()
                        .validation(validation)
                        .user(assignUser)
                        .assignmentRole(AssignmentRole.valueOf(assignDto.getAssignmentRole()))
                        .zone(assignZone)
                        .build();
                assignmentRepository.save(assignment);
            }
        }

        // Reload with relationships
        validation = findValidationOrThrow(validation.getId());

        // Fetch assignments directly from the repo. The managed `validation` entity's
        // assignments collection is the builder's empty ArrayList (not a PersistentBag),
        // so validation.getAssignments() would return empty and silently skip the loop.
        List<ValidationAssignment> freshAssignments =
                assignmentRepository.findByValidationIdWithDetails(validation.getId());
        log.info("Ticket {} — notification de {} affectation(s)",
                ticketCode, freshAssignments.size());

        // Notify each assigned user — each in its own try-catch so one failure
        // doesn't prevent the remaining technicians from being notified.
        for (ValidationAssignment assignment : freshAssignments) {
            try {
                messagingEventService.onTicketAssignment(assignment);
            } catch (Exception e) {
                log.warn("Notification d'affectation échouée pour user {}: {}",
                        assignment.getUser() != null ? assignment.getUser().getId() : "?",
                        e.getMessage(), e);
            }
        }

        log.info("Ticket créé: {} pour zone {} par {}",
                ticketCode, zone.getName(), createdBy.getUsername());

        return validationMapper.toResponseDTO(validation);
    }

    public List<ValidationResponseDTO> planWeek(TicketWeekPlanRequestDTO dto, Long createdByUserId) {
        List<ValidationResponseDTO> results = new ArrayList<>();
        for (TicketCreateRequestDTO ticketDto : dto.getTickets()) {
            ticketDto.setPlannedWeekStart(dto.getWeekStart());
            ticketDto.setPlannedWeekEnd(dto.getWeekEnd());
            results.add(createTicket(ticketDto, createdByUserId));
        }
        return results;
    }

    // ========================
    // TICKET WORKFLOW TRANSITIONS
    // ========================

    /**
     * TECH_PREP starts preparation → EN_ATTENTE_PREP
     */
    public ValidationResponseDTO startPrep(Long id) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.PLANIFIE, "démarrer la préparation");

        validation.setStatus(TicketStatus.EN_ATTENTE_PREP);
        validation = validationRepository.save(validation);

        try {
            messagingEventService.onTicketStatusChange(validation, TicketStatus.PLANIFIE, null);
        } catch (Exception e) {
            log.warn("Notification échouée: {}", e.getMessage());
        }

        log.info("Ticket {} → EN_ATTENTE_PREP", validation.getTicketCode());
        return validationMapper.toResponseDTO(validation);
    }

    /**
     * TECH_PREP confirms tools are available → PREP_VALIDEE
     */
    public ValidationResponseDTO validatePrep(Long id, PrepValidationRequestDTO dto, Long techPrepUserId) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.EN_ATTENTE_PREP, "valider la préparation");

        User techPrep = userRepository.findById(techPrepUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        validation.setToolsVerified(dto.getToolsAvailable());
        validation.setToolsVerifiedAt(LocalDateTime.now());
        validation.setToolsVerifiedBy(techPrep);
        validation.setPrepComments(dto.getPrepComments());

        if (Boolean.TRUE.equals(dto.getToolsAvailable())) {
            validation.setStatus(TicketStatus.PREP_VALIDEE);
            log.info("Ticket {} → PREP_VALIDEE par {}", validation.getTicketCode(), techPrep.getUsername());
        } else {
            // Tools not available — stay in EN_ATTENTE_PREP with comments
            log.warn("Ticket {} — outillage NON disponible: {}", validation.getTicketCode(), dto.getPrepComments());
        }

        // Mark TECH_PREP assignments as completed
        List<ValidationAssignment> prepAssignments = assignmentRepository
                .findByValidationIdAndAssignmentRole(id, AssignmentRole.TECH_PREPARATION);
        for (ValidationAssignment a : prepAssignments) {
            if (a.getUser().getId().equals(techPrepUserId)) {
                a.setStatus(AssignmentStatus.TERMINE);
                a.setCompletedAt(LocalDateTime.now());
                assignmentRepository.save(a);
            }
        }

        validation = validationRepository.save(validation);

        try {
            if (Boolean.TRUE.equals(dto.getToolsAvailable())) {
                messagingEventService.onPrepValidated(validation, techPrep);
            }
            messagingEventService.onTicketStatusChange(validation, TicketStatus.EN_ATTENTE_PREP, techPrep);
        } catch (Exception e) {
            log.warn("Notification échouée: {}", e.getMessage());
        }

        return validationMapper.toResponseDTO(validation);
    }

    /**
     * TECH_VAL starts the actual validation → EN_COURS
     */
    public ValidationResponseDTO startValidation(Long id) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.PREP_VALIDEE, "démarrer la validation");

        validation.setStatus(TicketStatus.EN_COURS);
        validation.setStartDate(LocalDateTime.now());
        validation = validationRepository.save(validation);

        // Mark TECH_VAL assignments as EN_COURS
        List<ValidationAssignment> valAssignments = assignmentRepository
                .findByValidationIdAndAssignmentRole(id, AssignmentRole.TECH_VALIDATION);
        for (ValidationAssignment a : valAssignments) {
            a.setStatus(AssignmentStatus.EN_COURS);
            assignmentRepository.save(a);
        }

        // Initial AI prediction
        try {
            aiPredictionService.predictNonConformity(validation);
        } catch (Exception e) {
            log.warn("Prédiction IA échouée pour ticket {}: {}", validation.getTicketCode(), e.getMessage());
        }

        try {
            messagingEventService.onTicketStatusChange(validation, TicketStatus.PREP_VALIDEE, null);
        } catch (Exception e) {
            log.warn("Notification échouée: {}", e.getMessage());
        }

        log.info("Ticket {} → EN_COURS", validation.getTicketCode());
        return validationMapper.toResponseDTO(findValidationOrThrow(id));
    }

    /**
     * TECH_VAL submits for review → EN_REVUE
     */
    public ValidationResponseDTO submitForReview(Long id) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.EN_COURS, "soumettre pour revue");

        validation.setStatus(TicketStatus.EN_REVUE);
        validation = validationRepository.save(validation);

        // Mark TECH_VAL assignments as completed
        List<ValidationAssignment> valAssignments = assignmentRepository
                .findByValidationIdAndAssignmentRole(id, AssignmentRole.TECH_VALIDATION);
        for (ValidationAssignment a : valAssignments) {
            a.setStatus(AssignmentStatus.TERMINE);
            a.setCompletedAt(LocalDateTime.now());
            assignmentRepository.save(a);
        }

        // Update AI prediction with final results
        try {
            aiPredictionService.predictNonConformity(validation);
        } catch (Exception e) {
            log.warn("Prédiction IA échouée: {}", e.getMessage());
        }

        try {
            messagingEventService.onTicketSubmittedForReview(validation, null);
            messagingEventService.onTicketStatusChange(validation, TicketStatus.EN_COURS, null);
        } catch (Exception e) {
            log.warn("Notification échouée: {}", e.getMessage());
        }

        log.info("Ticket {} → EN_REVUE", validation.getTicketCode());
        return validationMapper.toResponseDTO(findValidationOrThrow(id));
    }

    /**
     * CHEF_SECTEUR/EXPERT closes the ticket → CONFORME or NON_CONFORME
     */
    public ValidationResponseDTO closeTicket(Long id, TicketCloseRequestDTO dto) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.EN_REVUE, "clôturer le ticket");

        TicketStatus finalStatus = TicketStatus.valueOf(dto.getFinalStatus());
        if (finalStatus != TicketStatus.CONFORME && finalStatus != TicketStatus.NON_CONFORME) {
            throw new ValidationException("Le statut final doit être CONFORME ou NON_CONFORME");
        }

        validation.setStatus(finalStatus);
        validation.setEndDate(LocalDateTime.now());
        validation.setReviewComments(dto.getReviewComments());
        validation = validationRepository.save(validation);

        // Recalculate KPIs for the line
        try {
            Long lineId = validation.getValidationZone().getProductionLine().getId();
            kpiService.recalculateKPIsForLine(lineId);
        } catch (Exception e) {
            log.warn("Recalcul KPIs échoué: {}", e.getMessage());
        }

        log.info("Ticket {} → {} (cloturé)", validation.getTicketCode(), finalStatus);
        return validationMapper.toResponseDTO(findValidationOrThrow(id));
    }

    /**
     * Cancel the ticket → ANNULE
     */
    public ValidationResponseDTO cancelTicket(Long id, String reason) {
        Validation validation = findValidationOrThrow(id);

        if (validation.getStatus() == TicketStatus.CONFORME ||
                validation.getStatus() == TicketStatus.NON_CONFORME) {
            throw new ValidationException("Impossible d'annuler un ticket déjà clôturé");
        }

        TicketStatus oldStatus = validation.getStatus();
        validation.setStatus(TicketStatus.ANNULE);
        validation.setEndDate(LocalDateTime.now());
        validation.setComments(validation.getComments() != null ?
                validation.getComments() + " | Annulé: " + reason : "Annulé: " + reason);
        validation = validationRepository.save(validation);

        try {
            messagingEventService.onTicketCancelled(validation, null, reason);
        } catch (Exception e) {
            log.warn("Notification annulation échouée: {}", e.getMessage());
        }

        log.info("Ticket {} → ANNULE: {}", validation.getTicketCode(), reason);
        return validationMapper.toResponseDTO(validation);
    }

    // ========================
    // HELPERS
    // ========================

    private Validation findValidationOrThrow(Long id) {
        return validationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Validation non trouvée avec l'id: " + id));
    }

    private void assertStatus(Validation validation, TicketStatus expectedStatus, String action) {
        if (validation.getStatus() != expectedStatus) {
            throw new ValidationException(
                    String.format("Impossible de %s: le ticket %s est en statut %s (attendu: %s)",
                            action, validation.getTicketCode(), validation.getStatus(), expectedStatus));
        }
    }
}