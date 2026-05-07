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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final ValidationRepository validationRepository;
    private final ValidationZoneRepository zoneRepository;
    private final ProductionLineRepository productionLineRepository;
    private final ValidationPosteStatusRepository posteStatusRepository;
    private final ValidationResultRepository validationResultRepository;
    private final UserRepository userRepository;
    private final ValidationAssignmentRepository assignmentRepository;
    private final ValidationMapper validationMapper;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final AIPredictionService aiPredictionService;
    private final KPIService kpiService;
    private final MessagingEventService messagingEventService;
    private final SimpMessagingTemplate messagingTemplate;
    private final HandoverService handoverService;
    private final com.pfe.sageline.Config.SecurityUtils securityUtils;

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
        // Normalize any day in the week to Monday → Sunday so the planner always
        // returns every ticket planned that calendar week, regardless of which
        // day the caller passed.
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        log.debug("getTicketsByWeek: week {} → {}", weekStart, weekEnd);
        return validationRepository.findByPlannedWeek(weekStart, weekEnd).stream()
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

    /**
     * Create a line-level ticket (2026-04 Sagemcom model).
     *
     * <p>One ticket covers an entire {@link ProductionLine}. Every required
     * poste (ValidationZone) of that line gets its own
     * {@link ValidationPosteStatus} sub-row so different technicians can close
     * different postes independently. The legacy single-zone ({@code
     * validationZone}) FK is populated with the line's first poste so that
     * {@code AIPredictionService} and zone-scoped KPI queries keep working
     * while the frontend migrates.
     */
    public ValidationResponseDTO createTicket(TicketCreateRequestDTO dto, Long createdByUserId) {
        // --- 1. Resolve line (canonical) -------------------------------------
        if (dto.getProductionLineId() == null) {
            throw new ValidationException("La ligne de production est obligatoire");
        }
        ProductionLine line = productionLineRepository.findById(dto.getProductionLineId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ligne non trouvée: " + dto.getProductionLineId()));

        // --- 2. Load every poste of the line, ordered -----------------------
        List<ValidationZone> lineZones = zoneRepository.findByProductionLineId(line.getId())
                .stream()
                .sorted(Comparator.comparing(
                        ValidationZone::getOrderInLine,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ValidationZone::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (lineZones.isEmpty()) {
            throw new ValidationException(
                    "La ligne " + line.getCode() +
                    " n'a aucun poste configuré — impossible de créer un ticket");
        }

        // --- 2b. Optional poste selection -----------------------------------
        // If the client explicitly passes includedZoneIds, trim the line's
        // postes to that subset. Null/empty preserves the default "include
        // every poste" behaviour. Every requested id must belong to the line
        // — cross-line ids are a client bug, not a silent no-op.
        List<Long> requestedZoneIds = dto.getIncludedZoneIds();
        List<ValidationZone> coveredZones;
        if (requestedZoneIds != null && !requestedZoneIds.isEmpty()) {
            java.util.Set<Long> allLineIds = lineZones.stream()
                    .map(ValidationZone::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<Long> foreign = requestedZoneIds.stream()
                    .filter(id -> id != null && !allLineIds.contains(id))
                    .toList();
            if (!foreign.isEmpty()) {
                throw new ValidationException(
                        "Postes invalides pour la ligne " + line.getCode() + ": " + foreign);
            }
            java.util.Set<Long> requestedSet = new java.util.HashSet<>(requestedZoneIds);
            coveredZones = lineZones.stream()
                    .filter(z -> requestedSet.contains(z.getId()))
                    .toList();
            if (coveredZones.isEmpty()) {
                throw new ValidationException(
                        "Au moins un poste doit être inclus dans le ticket");
            }
            log.info("Ticket sur ligne {} — sous-ensemble de postes: {}/{}",
                    line.getCode(), coveredZones.size(), lineZones.size());
        } else {
            coveredZones = lineZones;
        }

        // Primary zone = explicit client choice, or first of the covered set.
        // When the user excluded the default first poste, fall back to the
        // first *covered* one so legacy code paths don't point at an excluded
        // zone.
        final Long explicitZoneId = dto.getValidationZoneId();
        ValidationZone primaryZone = coveredZones.stream()
                .filter(z -> explicitZoneId != null && z.getId().equals(explicitZoneId))
                .findFirst()
                .orElse(coveredZones.get(0));

        // --- 3. Prevent duplicate active line-tickets for the same week -----
        LocalDate dupKey = dto.getPlannedWeekStart() != null
                ? dto.getPlannedWeekStart()
                : dto.getPlannedDate();
        if (dupKey != null) {
            LocalDate weekStart = dupKey.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);
            List<Validation> existing = validationRepository
                    .findActiveByLineAndWeek(line.getId(), weekStart, weekEnd);
            if (!existing.isEmpty()) {
                Validation clash = existing.get(0);
                throw new ValidationException(
                        "Un ticket actif existe déjà pour la ligne " + line.getCode() +
                        " sur la semaine du " + weekStart + " (" + clash.getTicketCode() + ")");
            }
        }

        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        String ticketCode = ticketCodeGenerator.generate();

        // --- 4. Persist the Validation header -------------------------------
        Validation validation = validationMapper.toEntity(dto, line, primaryZone, createdBy, ticketCode);
        validation = validationRepository.save(validation);

        // --- 5. Seed one ValidationPosteStatus per COVERED poste ------------
        // Excluded postes (not in coveredZones) intentionally get no sub-row,
        // so markPosteDone/closeTicket will never block on them.
        for (ValidationZone z : coveredZones) {
            ValidationPosteStatus row = ValidationPosteStatus.builder()
                    .validation(validation)
                    .zone(z)
                    .status(TicketStatus.PLANIFIE)
                    .orderInLine(z.getOrderInLine())
                    .build();
            posteStatusRepository.save(row);
        }

        // --- 6. Create assignments ------------------------------------------
        if (dto.getAssignments() != null && !dto.getAssignments().isEmpty()) {
            for (TicketCreateRequestDTO.TicketAssignmentDTO assignDto : dto.getAssignments()) {
                User assignUser = userRepository.findById(assignDto.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Utilisateur non trouvé: " + assignDto.getUserId()));

                // For a line-ticket, the assignment zone defaults to the primary
                // poste. Clients can still pin an assignment to a specific poste
                // by passing zoneId (same behaviour as before).
                Long assignZoneId = assignDto.getZoneId() != null
                        ? assignDto.getZoneId()
                        : primaryZone.getId();
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
        // MessagingEventService.onTicketAssignment dedupes per (user, ticket)
        // so a tech assigned to several postes of the same line only sees one
        // conversation / one notification (M2 group semantics).
        for (ValidationAssignment assignment : freshAssignments) {
            try {
                messagingEventService.onTicketAssignment(assignment);
            } catch (Exception e) {
                log.warn("Notification d'affectation échouée pour user {}: {}",
                        assignment.getUser() != null ? assignment.getUser().getId() : "?",
                        e.getMessage(), e);
            }
        }

        log.info("Ticket créé: {} pour ligne {} ({}/{} postes) par {}",
                ticketCode, line.getCode(), coveredZones.size(), lineZones.size(),
                createdBy.getUsername());

        broadcastTicketUpdate(validation);

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

        broadcastTicketUpdate(validation);

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

        broadcastTicketUpdate(validation);

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

        broadcastTicketUpdate(validation);

        log.info("Ticket {} → EN_COURS", validation.getTicketCode());
        return validationMapper.toResponseDTO(findValidationOrThrow(id));
    }

    /**
     * TECH_VAL submits for review → EN_REVUE.
     * FR-006a: if the ticket is EN_ATTENTE_HANDOVER, the original assignee may
     * still submit for review, which auto-cancels the pending handover first.
     */
    public ValidationResponseDTO submitForReview(Long id) {
        Validation validation = findValidationOrThrow(id);

        if (validation.getStatus() == TicketStatus.EN_ATTENTE_HANDOVER) {
            Long currentUserId = securityUtils.getCurrentUserId();
            boolean isOriginalTech = assignmentRepository.findByValidationId(id).stream()
                    .anyMatch(a -> a.getUser().getId().equals(currentUserId)
                            && a.getStatus() == AssignmentStatus.PAUSED);
            if (!isOriginalTech) {
                throw new ValidationException(
                        "Le ticket " + validation.getTicketCode() +
                        " est en attente de passation — seul le technicien d'origine peut le soumettre en revue (FR-006)");
            }
            handoverService.autoCancelIfPending(validation);
            // Reload after auto-cancel changed the status back to EN_COURS
            validation = findValidationOrThrow(id);
        }

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

        broadcastTicketUpdate(validation);

        log.info("Ticket {} → EN_REVUE", validation.getTicketCode());
        return validationMapper.toResponseDTO(findValidationOrThrow(id));
    }

    /**
     * CHEF_SECTEUR/EXPERT closes the line-ticket → CONFORME or NON_CONFORME.
     *
     * <p>Safety rule: the ticket can only be closed when every
     * {@link ValidationPosteStatus} row is terminal (CONFORME or NON_CONFORME).
     * Tickets migrated from the pre-2026-04 model have zero poste rows and
     * keep the legacy behaviour (close with the global final status only).
     */
    public ValidationResponseDTO closeTicket(Long id, TicketCloseRequestDTO dto) {
        Validation validation = findValidationOrThrow(id);
        assertStatus(validation, TicketStatus.EN_REVUE, "clôturer le ticket");

        TicketStatus finalStatus = TicketStatus.valueOf(dto.getFinalStatus());
        if (finalStatus != TicketStatus.CONFORME && finalStatus != TicketStatus.NON_CONFORME) {
            throw new ValidationException("Le statut final doit être CONFORME ou NON_CONFORME");
        }

        // Enforce: all postes must have reached a terminal state before we can
        // close the parent ticket. This only fires for new-model tickets that
        // actually have poste rows.
        long totalPostes = posteStatusRepository.countByValidationId(id);
        if (totalPostes > 0) {
            long donePostes = posteStatusRepository.countByValidationIdAndStatusIn(
                    id, List.of(TicketStatus.CONFORME, TicketStatus.NON_CONFORME));
            if (donePostes < totalPostes) {
                throw new ValidationException(
                        "Impossible de clôturer le ticket " + validation.getTicketCode() +
                        ": " + donePostes + "/" + totalPostes +
                        " postes terminés. Tous les postes doivent être CONFORME ou NON_CONFORME.");
            }
            // If the chef left the finalStatus as CONFORME but at least one
            // poste is NON_CONFORME, force the ticket to NON_CONFORME — a line
            // can't pass if any of its postes failed.
            long nonConformePostes = posteStatusRepository.countByValidationIdAndStatus(
                    id, TicketStatus.NON_CONFORME);
            if (nonConformePostes > 0 && finalStatus == TicketStatus.CONFORME) {
                log.info("Ticket {} clôture forcée à NON_CONFORME: {} poste(s) NC",
                        validation.getTicketCode(), nonConformePostes);
                finalStatus = TicketStatus.NON_CONFORME;
            }
        }

        // Data-quality guard: a line can only be declared CONFORME if at least
        // one measurement was actually recorded for the ticket. ValidationResult
        // rows are ticket-scoped (no zone FK), so we check at the ticket level.
        // NON_CONFORME closures are still allowed without results — a line can
        // be rejected because no measurements could be taken.
        if (finalStatus == TicketStatus.CONFORME) {
            int resultsCount = validationResultRepository.findByValidationId(id).size();
            if (resultsCount == 0) {
                throw new ValidationException(
                        "Impossible de clôturer le ticket " + validation.getTicketCode() +
                        " en CONFORME sans aucune mesure enregistrée. " +
                        "Saisissez au moins un résultat de validation avant la clôture.");
            }
        }

        TicketStatus previousStatus = validation.getStatus();
        validation.setStatus(finalStatus);
        validation.setEndDate(LocalDateTime.now());
        validation.setReviewComments(dto.getReviewComments());
        validationRepository.save(validation);

        // Finalize every non-terminal assignment so the "Mes tickets" list
        // doesn't keep showing the ticket as EN_COURS for TECH_VAL/TECH_PREP
        // after the chef closes it.
        finalizeAssignmentsForClosure(id);

        // Re-fetch with associations for KPI calc, notifications and WebSocket
        // broadcast — assignments must be eagerly loaded so the messaging
        // service can reach every assigned tech.
        Validation reloaded = findValidationOrThrow(id);

        // Recalculate KPIs for the line (prefer direct FK, fall back to legacy chain)
        try {
            Long lineId = resolveLineId(reloaded);
            if (lineId != null) {
                kpiService.recalculateKPIsForLine(lineId);
            }
        } catch (Exception e) {
            log.warn("Recalcul KPIs échoué: {}", e.getMessage());
        }

        // Notify assigned techs + creator that the ticket has been closed.
        try {
            messagingEventService.onTicketStatusChange(reloaded, previousStatus, null);
        } catch (Exception e) {
            log.warn("Notification de clôture échouée: {}", e.getMessage());
        }

        broadcastTicketUpdate(reloaded);

        log.info("Ticket {} → {} (cloturé)", reloaded.getTicketCode(), finalStatus);
        return validationMapper.toResponseDTO(reloaded);
    }

    /**
     * Mark a single poste inside a line-ticket as CONFORME or NON_CONFORME.
     * Used by TECH_VAL (or TECH_PREP for prep-only postes) to close each
     * poste independently. When the last poste reaches a terminal state the
     * parent ticket is auto-advanced to {@link TicketStatus#EN_REVUE} so the
     * CHEF_SECTEUR/EXPERT can close it.
     */
    public ValidationResponseDTO markPosteDone(Long validationId,
                                               Long zoneId,
                                               String finalStatusRaw,
                                               String notes,
                                               Long actingUserId) {
        Validation validation = findValidationOrThrow(validationId);

        if (validation.getStatus() != TicketStatus.EN_COURS
                && validation.getStatus() != TicketStatus.EN_REVUE) {
            throw new ValidationException(
                    "Impossible de clôturer un poste: le ticket " + validation.getTicketCode() +
                    " est en statut " + validation.getStatus() +
                    " (attendu: EN_COURS ou EN_REVUE)");
        }

        TicketStatus finalStatus;
        try {
            finalStatus = TicketStatus.valueOf(finalStatusRaw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Statut invalide: " + finalStatusRaw);
        }
        if (finalStatus != TicketStatus.CONFORME && finalStatus != TicketStatus.NON_CONFORME) {
            throw new ValidationException(
                    "Le statut final d'un poste doit être CONFORME ou NON_CONFORME");
        }

        ValidationPosteStatus row = posteStatusRepository
                .findByValidationIdAndZoneId(validationId, zoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poste " + zoneId + " non lié au ticket " + validation.getTicketCode()));

        if (row.getStatus() == TicketStatus.CONFORME
                || row.getStatus() == TicketStatus.NON_CONFORME) {
            throw new ValidationException(
                    "Le poste " + (row.getZone() != null ? row.getZone().getName() : zoneId) +
                    " est déjà clôturé (" + row.getStatus() + ")");
        }

        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        row.setStatus(finalStatus);
        row.setValidatedBy(actor);
        row.setValidatedAt(LocalDateTime.now());
        if (notes != null && !notes.isBlank()) {
            row.setNotes(notes);
        }
        posteStatusRepository.save(row);

        // Auto-advance the parent ticket to EN_REVUE once every poste is done.
        long total = posteStatusRepository.countByValidationId(validationId);
        long done = posteStatusRepository.countByValidationIdAndStatusIn(
                validationId, List.of(TicketStatus.CONFORME, TicketStatus.NON_CONFORME));
        if (total > 0 && done == total
                && validation.getStatus() == TicketStatus.EN_COURS) {
            validation.setStatus(TicketStatus.EN_REVUE);
            validationRepository.save(validation);
            // Re-fetch with LEFT JOIN FETCH so assignments are loaded before
            // we hand the entity to messagingEventService (which iterates
            // validation.getAssignments() to notify each assigned tech).
            Validation reloaded = findValidationOrThrow(validationId);
            try {
                messagingEventService.onTicketSubmittedForReview(reloaded, actor);
                messagingEventService.onTicketStatusChange(reloaded, TicketStatus.EN_COURS, actor);
            } catch (Exception e) {
                log.warn("Notification auto EN_REVUE échouée: {}", e.getMessage());
            }
            log.info("Ticket {} → EN_REVUE (tous les postes clôturés)",
                    reloaded.getTicketCode());
            broadcastTicketUpdate(reloaded);
        } else {
            broadcastTicketUpdate(validation);
        }

        log.info("Ticket {} — poste {} → {} par {}",
                validation.getTicketCode(), zoneId, finalStatus, actor.getUsername());
        return validationMapper.toResponseDTO(findValidationOrThrow(validationId));
    }

    /**
     * Resolve the owning line id, preferring the new {@code productionLine}
     * FK and falling back to the legacy {@code validationZone.productionLine}
     * chain for any row that hasn't been migrated yet.
     */
    private Long resolveLineId(Validation validation) {
        if (validation == null) return null;
        if (validation.getProductionLine() != null) {
            return validation.getProductionLine().getId();
        }
        if (validation.getValidationZone() != null
                && validation.getValidationZone().getProductionLine() != null) {
            return validation.getValidationZone().getProductionLine().getId();
        }
        return null;
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

        // FR-006a: auto-cancel any pending handover before cancelling the ticket;
        // if this throws, the whole transaction rolls back (intentional).
        if (validation.getStatus() == TicketStatus.EN_ATTENTE_HANDOVER) {
            handoverService.autoCancelIfPending(validation);
            validation = findValidationOrThrow(id);
        }

        TicketStatus oldStatus = validation.getStatus();
        validation.setStatus(TicketStatus.ANNULE);
        validation.setEndDate(LocalDateTime.now());
        validation.setComments(validation.getComments() != null ?
                validation.getComments() + " | Annulé: " + reason : "Annulé: " + reason);
        validation = validationRepository.save(validation);

        // Any open assignments should no longer show as "in progress" — we mark
        // them TERMINE (the enum has no ANNULE value). Consumers that care
        // about the distinction can read Validation.status = ANNULE.
        finalizeAssignmentsForClosure(id);

        try {
            messagingEventService.onTicketCancelled(validation, null, reason);
        } catch (Exception e) {
            log.warn("Notification annulation échouée: {}", e.getMessage());
        }

        broadcastTicketUpdate(validation);

        log.info("Ticket {} → ANNULE: {}", validation.getTicketCode(), reason);
        return validationMapper.toResponseDTO(validation);
    }

    /**
     * Mark every non-terminal assignment of the ticket as TERMINE so that
     * "Mes tickets" / assignment badges stop showing the ticket as active
     * after the parent ticket reaches a terminal status (CONFORME,
     * NON_CONFORME, or ANNULE).
     *
     * <p>Safe to call multiple times — rows already in TERMINE are skipped.
     */
    private void finalizeAssignmentsForClosure(Long validationId) {
        List<ValidationAssignment> all = assignmentRepository.findByValidationId(validationId);
        LocalDateTime now = LocalDateTime.now();
        for (ValidationAssignment a : all) {
            if (a.getStatus() != AssignmentStatus.TERMINE) {
                a.setStatus(AssignmentStatus.TERMINE);
                if (a.getCompletedAt() == null) {
                    a.setCompletedAt(now);
                }
                assignmentRepository.save(a);
            }
        }
    }

    // ========================
    // HELPERS
    // ========================

    /**
     * Broadcast a fresh ticket DTO over WebSocket so every open session
     * (chef_secteur, tech_val, tech_prep) refreshes in real time.
     *
     * - /topic/tickets.{id}  → for ticket-detail subscribers
     * - /topic/tickets       → for ticket-list subscribers
     */
    private void broadcastTicketUpdate(Validation validation) {
        if (validation == null || validation.getId() == null) return;
        try {
            ValidationResponseDTO dto = validationMapper.toResponseDTO(validation);
            messagingTemplate.convertAndSend("/topic/tickets." + validation.getId(), dto);
            messagingTemplate.convertAndSend("/topic/tickets", dto);
        } catch (Exception e) {
            log.warn("Broadcast ticket échoué pour {}: {}",
                    validation.getTicketCode(), e.getMessage());
        }
    }

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