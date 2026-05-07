package com.pfe.sageline.service.impl;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.HandoverAssignRequest;
import com.pfe.sageline.dtos.request.HandoverInitiateRequest;
import com.pfe.sageline.dtos.response.HandoverKpiResponse;
import com.pfe.sageline.dtos.response.HandoverNotificationDto;
import com.pfe.sageline.dtos.response.HandoverResponse;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.HandoverMapper;
import com.pfe.sageline.repository.*;
import com.pfe.sageline.service.HandoverService;
import com.pfe.sageline.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HandoverServiceImpl implements HandoverService {

    private static final String MSG_TRIGGERED_AUTO =
            "Votre shift se termine à 17h00. Veuillez compléter votre note de passation pour le ticket %s.";
    private static final String MSG_TRIGGERED_MANUAL =
            "Passation initiée pour le ticket %s.";
    private static final String MSG_TRIGGERED_FORCE =
            "Une passation a été forcée par l'administration pour le ticket %s.";
    private static final String MSG_TRIGGERED_SECTEUR =
            "Le ticket %s nécessite une passation.";
    private static final String MSG_TRIGGERED_SECTEUR_FORCE =
            "Le ticket %s nécessite une passation (forcée).";
    private static final String MSG_ASSIGNED_PERSONAL =
            "Le ticket %s vous a été assigné en passation.";
    private static final String MSG_ASSIGNED_SECTEUR =
            "Le ticket %s a été assigné à %s.";
    private static final String MSG_ACCEPTED_SECTEUR =
            "Le ticket %s a été repris par %s.";
    private static final String MSG_CANCELLED_PERSONAL =
            "La passation pour %s a été annulée — vous êtes à nouveau l'assigné actif.";
    private static final String MSG_CANCELLED_SECTEUR =
            "Passation annulée pour %s.";

    private final HandoverRepository handoverRepository;
    private final ValidationRepository validationRepository;
    private final ValidationAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityUtils securityUtils;
    private final HandoverMapper handoverMapper;

    // ─── US1: Automated handover (SHIFT_END_AUTO) ───────────────────────────

    @Override
    public void triggerAutoHandover(Validation ticket) {
        if (handoverRepository.findActiveByValidation(ticket.getId()).isPresent()) {
            log.debug("Ticket {} already has an active handover — skipping", ticket.getTicketCode());
            return;
        }

        ValidationAssignment activeAssignment = assignmentRepository.findByValidationId(ticket.getId()).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.EN_COURS)
                .findFirst()
                .orElse(null);

        if (activeAssignment == null) {
            log.warn("Ticket {} has no EN_COURS assignment — cannot trigger handover", ticket.getTicketCode());
            return;
        }

        User fromTech = activeAssignment.getUser();
        TicketHandover handover = TicketHandover.builder()
                .validation(ticket)
                .fromTech(fromTech)
                .triggeredBy(TriggerType.SHIFT_END_AUTO)
                .status(HandoverStatus.PENDING)
                .scheduledAt(LocalDateTime.now())
                .build();

        ticket.setStatus(TicketStatus.EN_ATTENTE_HANDOVER);
        activeAssignment.setStatus(AssignmentStatus.PAUSED);

        TicketHandover saved = handoverRepository.save(handover);
        assignmentRepository.save(activeAssignment);
        validationRepository.save(ticket);

        Long handoverId = saved.getId();
        Long validationId = ticket.getId();
        String ticketCode = ticket.getTicketCode();
        Long fromTechId = fromTech.getId();
        Long secteurId = resolveSecteurId(ticket);

        afterCommit(() -> {
            notificationService.createAndSend(
                    fromTechId,
                    "Passation de poste",
                    String.format(MSG_TRIGGERED_AUTO, ticketCode),
                    NotificationType.HANDOVER_TRIGGERED,
                    handoverId, "HANDOVER");

            sendPersonalEvent(fromTechId, new HandoverNotificationDto(
                    "HANDOVER_TRIGGERED", handoverId, validationId, ticketCode,
                    String.format(MSG_TRIGGERED_AUTO, ticketCode), LocalDateTime.now()));

            if (secteurId != null) {
                sendSecteurEvent(secteurId, new HandoverNotificationDto(
                        "HANDOVER_TRIGGERED", handoverId, validationId, ticketCode,
                        String.format(MSG_TRIGGERED_SECTEUR, ticketCode), LocalDateTime.now()));
            }
        });

        log.info("Handover SHIFT_END_AUTO créé pour ticket {} (tech: {})",
                ticketCode, fromTech.getUsername());
    }

    // ─── US3/US5: Manual or force handover ──────────────────────────────────

    @Override
    public HandoverResponse initiateHandover(Long validationId, HandoverInitiateRequest req, TriggerType trigger) {
        Validation validation = validationRepository.findById(validationId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation non trouvée: " + validationId));

        if (validation.getStatus() != TicketStatus.EN_COURS) {
            throw new ValidationException("Le ticket " + validation.getTicketCode() +
                    " doit être EN_COURS pour initier une passation (statut actuel: " + validation.getStatus() + ")");
        }

        if (handoverRepository.findActiveByValidation(validationId).isPresent()) {
            throw new ValidationException("Une passation est déjà en cours pour le ticket " + validation.getTicketCode());
        }

        List<ValidationAssignment> assignments = assignmentRepository.findByValidationId(validationId);
        ValidationAssignment activeAssignment = assignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.EN_COURS)
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Aucune affectation active (EN_COURS) trouvée pour le ticket " + validation.getTicketCode()));

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));

        if (trigger == TriggerType.MANUAL) {
            if (!activeAssignment.getUser().getId().equals(currentUserId)) {
                throw new ValidationException(
                        "Seul le technicien assigné peut initier une passation manuelle (FR-016)");
            }
        }

        if (req != null && req.handoverNote() != null) {
            activeAssignment.setHandoverNote(req.handoverNote());
        }
        activeAssignment.setStatus(AssignmentStatus.PAUSED);
        assignmentRepository.save(activeAssignment);

        validation.setStatus(TicketStatus.EN_ATTENTE_HANDOVER);
        validationRepository.save(validation);

        TicketHandover handover = TicketHandover.builder()
                .validation(validation)
                .fromTech(activeAssignment.getUser())
                .handoverNote(req != null ? req.handoverNote() : null)
                .progressSummary(req != null ? req.progressSummary() : null)
                .triggeredBy(trigger)
                .status(HandoverStatus.PENDING)
                .scheduledAt(LocalDateTime.now())
                .build();

        TicketHandover saved = handoverRepository.save(handover);

        Long handoverId = saved.getId();
        Long fromTechId = activeAssignment.getUser().getId();
        String ticketCode = validation.getTicketCode();
        Long secteurId = resolveSecteurId(validation);
        TriggerType finalTrigger = trigger;

        afterCommit(() -> {
            String personalMsg = switch (finalTrigger) {
                case MANUAL -> String.format(MSG_TRIGGERED_MANUAL, ticketCode);
                case ADMIN_FORCE -> String.format(MSG_TRIGGERED_FORCE, ticketCode);
                default -> String.format(MSG_TRIGGERED_AUTO, ticketCode);
            };
            String secteurMsg = finalTrigger == TriggerType.ADMIN_FORCE
                    ? String.format(MSG_TRIGGERED_SECTEUR_FORCE, ticketCode)
                    : String.format(MSG_TRIGGERED_SECTEUR, ticketCode);

            notificationService.createAndSend(
                    fromTechId, "Passation de poste", personalMsg,
                    NotificationType.HANDOVER_TRIGGERED, handoverId, "HANDOVER");

            sendPersonalEvent(fromTechId, new HandoverNotificationDto(
                    "HANDOVER_TRIGGERED", handoverId, validationId, ticketCode,
                    personalMsg, LocalDateTime.now()));

            if (secteurId != null) {
                sendSecteurEvent(secteurId, new HandoverNotificationDto(
                        "HANDOVER_TRIGGERED", handoverId, validationId, ticketCode,
                        secteurMsg, LocalDateTime.now()));
            }
        });

        log.info("Handover {} créé pour ticket {} (tech: {})", trigger, ticketCode,
                activeAssignment.getUser().getUsername());
        return handoverMapper.toResponse(saved);
    }

    // ─── US2: Self-accept ───────────────────────────────────────────────────

    @Override
    public HandoverResponse acceptHandover(Long handoverId) {
        TicketHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Passation introuvable: " + handoverId));

        Validation validation = handover.getValidation();
        Long validationId = validation.getId();

        Long currentUserId = securityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));

        // Zone-locality check (FR-019a): user must have at least one assignment on the
        // same production line as the ticket. Resolved via direct FK first, then the
        // legacy validationZone chain. If neither is set we cannot enforce the guard
        // and throw to be safe.
        Long lineId;
        if (validation.getProductionLine() != null) {
            lineId = validation.getProductionLine().getId();
        } else if (validation.getValidationZone() != null
                && validation.getValidationZone().getProductionLine() != null) {
            lineId = validation.getValidationZone().getProductionLine().getId();
        } else {
            throw new ValidationException("cross-zone self-accept not allowed");
        }
        boolean inZone = assignmentRepository.findByUserId(currentUserId).stream()
                .anyMatch(a -> a.getZone() != null
                        && a.getZone().getProductionLine() != null
                        && a.getZone().getProductionLine().getId().equals(lineId));
        if (!inZone) {
            throw new ValidationException("cross-zone self-accept not allowed");
        }

        // Guard: no other ACTIVE assignment on this ticket
        boolean alreadyActive = assignmentRepository.findByValidationId(validationId).stream()
                .anyMatch(a -> a.getStatus() == AssignmentStatus.EN_COURS);
        if (alreadyActive) {
            throw new ValidationException("Le ticket possède déjà un technicien actif");
        }

        HandoverStatus expected = handover.getStatus();
        if (expected != HandoverStatus.PENDING && expected != HandoverStatus.ACCEPTED) {
            throw new ValidationException("La passation ne peut plus être acceptée (statut: " + expected + ")");
        }
        if (expected == HandoverStatus.ACCEPTED
                && (handover.getToTech() == null || !handover.getToTech().getId().equals(currentUserId))) {
            throw new ValidationException("Cette passation est déjà assignée à un autre technicien");
        }

        int updated = handoverRepository.compareAndSetStatus(
                handoverId, expected, HandoverStatus.COMPLETED,
                currentUser, LocalDateTime.now());

        if (updated == 0) {
            throw new ValidationException("already accepted");
        }

        // New active assignment for the accepting tech
        ValidationAssignment newAssignment = ValidationAssignment.builder()
                .validation(validation)
                .user(currentUser)
                .assignmentRole(AssignmentRole.TECH_VALIDATION)
                .zone(handover.getFromTech().equals(currentUser)
                        ? findPausedAssignment(validationId, handover.getFromTech()).map(a -> a.getZone()).orElse(null)
                        : findFirstAssignmentZone(validationId))
                .status(AssignmentStatus.EN_COURS)
                .build();
        assignmentRepository.save(newAssignment);

        validation.setStatus(TicketStatus.EN_COURS);
        validationRepository.save(validation);

        // Update in-memory state to reflect the CAS result (avoids a reload roundtrip)
        handover.setStatus(HandoverStatus.COMPLETED);
        handover.setToTech(currentUser);
        handover.setAcceptedAt(LocalDateTime.now());

        String ticketCode = validation.getTicketCode();
        Long secteurId = resolveSecteurId(validation);
        String username = currentUser.getUsername();

        afterCommit(() -> {
            if (secteurId != null) {
                sendSecteurEvent(secteurId, new HandoverNotificationDto(
                        "HANDOVER_ACCEPTED", handoverId, validationId, ticketCode,
                        String.format(MSG_ACCEPTED_SECTEUR, ticketCode, username), LocalDateTime.now()));
            }
        });

        log.info("Passation {} acceptée par {}", handoverId, currentUser.getUsername());
        return handoverMapper.toResponse(handover);
    }

    // ─── US4: Supervisor assigns a tech ─────────────────────────────────────

    @Override
    public HandoverResponse assignHandover(Long handoverId, HandoverAssignRequest req) {
        TicketHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Passation introuvable: " + handoverId));

        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new ValidationException(
                    "Seule une passation PENDING peut être assignée (statut actuel: " + handover.getStatus() + ")");
        }

        User targetUser = userRepository.findById(req.techId())
                .orElseThrow(() -> new ResourceNotFoundException("Technicien introuvable: " + req.techId()));

        if (targetUser.getRole() != Role.TECH_VAL) {
            throw new ValidationException(
                    "L'utilisateur " + targetUser.getUsername() + " n'est pas TECH_VAL");
        }

        int updated = handoverRepository.compareAndSetStatus(
                handoverId, HandoverStatus.PENDING, HandoverStatus.ACCEPTED,
                targetUser, null);

        if (updated == 0) {
            throw new ValidationException("La passation a déjà été modifiée — veuillez actualiser");
        }

        // Update in-memory state to reflect the CAS result
        handover.setStatus(HandoverStatus.ACCEPTED);
        handover.setToTech(targetUser);

        String ticketCode = handover.getValidation().getTicketCode();
        Long validationId = handover.getValidation().getId();
        Long secteurId = resolveSecteurId(handover.getValidation());
        Long techId = targetUser.getId();
        String techUsername = targetUser.getUsername();

        afterCommit(() -> {
            notificationService.createAndSend(
                    techId, "Passation assignée",
                    String.format(MSG_ASSIGNED_PERSONAL, ticketCode),
                    NotificationType.HANDOVER_ASSIGNED, handoverId, "HANDOVER");

            sendPersonalEvent(techId, new HandoverNotificationDto(
                    "HANDOVER_ASSIGNED", handoverId, validationId, ticketCode,
                    String.format(MSG_ASSIGNED_PERSONAL, ticketCode), LocalDateTime.now()));

            if (secteurId != null) {
                sendSecteurEvent(secteurId, new HandoverNotificationDto(
                        "HANDOVER_ASSIGNED", handoverId, validationId, ticketCode,
                        String.format(MSG_ASSIGNED_SECTEUR, ticketCode, techUsername), LocalDateTime.now()));
            }
        });

        log.info("Passation {} assignée à {}", handoverId, targetUser.getUsername());
        return handoverMapper.toResponse(handover);
    }

    // ─── US6: Cancel handover ───────────────────────────────────────────────

    @Override
    public HandoverResponse cancelHandover(Long handoverId) {
        TicketHandover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Passation introuvable: " + handoverId));

        cancelHandoverInternal(handover);

        return handoverMapper.toResponse(handover);
    }

    @Override
    public void autoCancelIfPending(Validation validation) {
        handoverRepository.findActiveByValidation(validation.getId())
                .ifPresent(this::cancelHandoverInternal);
    }

    private void cancelHandoverInternal(TicketHandover handover) {
        if (handover.getStatus() != HandoverStatus.PENDING
                && handover.getStatus() != HandoverStatus.ACCEPTED) {
            throw new ValidationException(
                    "Impossible d'annuler une passation au statut " + handover.getStatus());
        }

        handover.setStatus(HandoverStatus.CANCELLED);
        handoverRepository.save(handover);

        Validation validation = handover.getValidation();
        Long validationId = validation.getId();
        User originalTech = handover.getFromTech();

        // Reactivate the original tech's PAUSED assignment
        assignmentRepository.findByValidationId(validationId).stream()
                .filter(a -> a.getUser().getId().equals(originalTech.getId())
                        && a.getStatus() == AssignmentStatus.PAUSED)
                .findFirst()
                .ifPresent(a -> {
                    a.setStatus(AssignmentStatus.EN_COURS);
                    assignmentRepository.save(a);
                });

        validation.setStatus(TicketStatus.EN_COURS);
        validationRepository.save(validation);

        Long handoverId = handover.getId();
        String ticketCode = validation.getTicketCode();
        Long secteurId = resolveSecteurId(validation);
        Long originalTechId = originalTech.getId();

        afterCommit(() -> {
            notificationService.createAndSend(
                    originalTechId, "Passation annulée",
                    String.format(MSG_CANCELLED_PERSONAL, ticketCode),
                    NotificationType.HANDOVER_CANCELLED, handoverId, "HANDOVER");

            sendPersonalEvent(originalTechId, new HandoverNotificationDto(
                    "HANDOVER_CANCELLED", handoverId, validationId, ticketCode,
                    String.format(MSG_CANCELLED_PERSONAL, ticketCode), LocalDateTime.now()));

            if (secteurId != null) {
                sendSecteurEvent(secteurId, new HandoverNotificationDto(
                        "HANDOVER_CANCELLED", handoverId, validationId, ticketCode,
                        String.format(MSG_CANCELLED_SECTEUR, ticketCode), LocalDateTime.now()));
            }
        });

        log.info("Passation {} annulée pour ticket {}", handoverId, ticketCode);
    }

    // ─── US7: History & KPIs ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<HandoverResponse> getHandoverHistory(Long validationId) {
        return handoverRepository.findByValidationOrderByScheduledAtAsc(validationId)
                .stream()
                .map(handoverMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<HandoverResponse> getPendingHandovers() {
        return handoverRepository.findAllPending()
                .stream()
                .map(handoverMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public HandoverKpiResponse getHandoverKpis(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        List<TicketHandover> all = handoverRepository.findInRange(fromDt, toDt);

        // Exclude CANCELLED for frequency counts
        List<TicketHandover> counted = all.stream()
                .filter(h -> h.getStatus() != HandoverStatus.CANCELLED)
                .toList();

        long totalCount = counted.size();

        Map<TriggerType, Long> byTriggerType = counted.stream()
                .collect(Collectors.groupingBy(TicketHandover::getTriggeredBy, Collectors.counting()));

        // By zone (production line)
        Map<Long, List<TicketHandover>> byLineId = counted.stream()
                .filter(h -> h.getValidation() != null && h.getValidation().getProductionLine() != null)
                .collect(Collectors.groupingBy(h -> h.getValidation().getProductionLine().getId()));

        List<HandoverKpiResponse.ZoneCount> byZone = byLineId.entrySet().stream()
                .map(e -> {
                    String lineName = e.getValue().get(0).getValidation().getProductionLine().getName();
                    return new HandoverKpiResponse.ZoneCount(e.getKey(), lineName, e.getValue().size());
                })
                .sorted(Comparator.comparingLong(HandoverKpiResponse.ZoneCount::zoneId))
                .collect(Collectors.toList());

        // By technician (fromTech)
        Map<Long, List<TicketHandover>> byTechId = counted.stream()
                .filter(h -> h.getFromTech() != null)
                .collect(Collectors.groupingBy(h -> h.getFromTech().getId()));

        List<HandoverKpiResponse.TechnicianCount> byTechnician = byTechId.entrySet().stream()
                .map(e -> {
                    String username = e.getValue().get(0).getFromTech().getUsername();
                    return new HandoverKpiResponse.TechnicianCount(e.getKey(), username, e.getValue().size());
                })
                .sorted(Comparator.comparingLong(HandoverKpiResponse.TechnicianCount::userId))
                .collect(Collectors.toList());

        // Time-to-accept stats (COMPLETED rows only)
        List<Long> acceptSeconds = all.stream()
                .filter(h -> h.getStatus() == HandoverStatus.COMPLETED
                        && h.getScheduledAt() != null && h.getAcceptedAt() != null)
                .map(h -> Duration.between(h.getScheduledAt(), h.getAcceptedAt()).toSeconds())
                .sorted()
                .collect(Collectors.toList());

        HandoverKpiResponse.TimeToAcceptStats timeToAccept = computeStats(acceptSeconds);

        return new HandoverKpiResponse(from, to, totalCount, byTriggerType, byZone, byTechnician, timeToAccept);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HandoverKpiResponse.TimeToAcceptStats computeStats(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return new HandoverKpiResponse.TimeToAcceptStats(0, 0, 0);
        }
        int n = sorted.size();
        long median = sorted.get(n / 2);
        long p95 = sorted.get((int) Math.ceil(n * 0.95) - 1);
        return new HandoverKpiResponse.TimeToAcceptStats(n, median, p95);
    }

    private Long resolveLineId(Validation validation) {
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
     * Walks productionLine -> phase -> secteur to resolve the secteur ID
     * that owns this validation. Used for the supervisor's WebSocket topic
     * (/topic/handover.secteur.{secteurId}).
     */
    private Long resolveSecteurId(Validation validation) {
        ProductionLine line = null;
        if (validation.getProductionLine() != null) {
            line = validation.getProductionLine();
        } else if (validation.getValidationZone() != null) {
            line = validation.getValidationZone().getProductionLine();
        }
        if (line == null || line.getPhase() == null || line.getPhase().getSecteur() == null) {
            return null;
        }
        return line.getPhase().getSecteur().getId();
    }

    private Optional<ValidationAssignment> findPausedAssignment(Long validationId, User tech) {
        return assignmentRepository.findByValidationId(validationId).stream()
                .filter(a -> a.getUser().getId().equals(tech.getId())
                        && a.getStatus() == AssignmentStatus.PAUSED)
                .findFirst();
    }

    private ValidationZone findFirstAssignmentZone(Long validationId) {
        return assignmentRepository.findByValidationId(validationId).stream()
                .map(ValidationAssignment::getZone)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void sendPersonalEvent(Long userId, HandoverNotificationDto dto) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), "/queue/handover", dto);
        } catch (Exception e) {
            log.warn("Envoi personal STOMP échoué pour user {}: {}", userId, e.getMessage());
        }
    }

    private void sendSecteurEvent(Long secteurId, HandoverNotificationDto dto) {
        try {
            messagingTemplate.convertAndSend("/topic/handover.secteur." + secteurId, dto);
        } catch (Exception e) {
            log.warn("Envoi secteur STOMP échoué pour secteur {}: {}", secteurId, e.getMessage());
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No active transaction (e.g., unit tests or misconfigured caller) — run inline.
            try { action.run(); } catch (Exception e) { log.warn("Post-commit action failed: {}", e.getMessage()); }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.warn("Erreur après commit dans HandoverService: {}", e.getMessage());
                }
            }
        });
    }
}
