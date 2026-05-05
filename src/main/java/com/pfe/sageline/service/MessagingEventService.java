package com.pfe.sageline.service;

import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.*;
import com.pfe.sageline.repository.ValidationAssignmentRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessagingEventService {

    private final MessageService messageService;
    private final NotificationService notificationService;
    private final ValidationAssignmentRepository assignmentRepository;

    /**
     * Appelé quand un utilisateur est affecté à une ligne de production.
     * Crée une conversation et envoie un message automatique.
     */
    public void onLineAssignment(User assignedUser, ProductionLine line, User assignedBy) {
        log.info("Événement: {} affecté à la ligne {} par {}",
                assignedUser.getUsername(), line.getCode(), assignedBy.getUsername());

        // 1. Créer ou récupérer la conversation
        Conversation conversation = messageService.createConversation(
                assignedBy.getId(),
                assignedUser.getId(),
                ConversationType.LINE_ASSIGNMENT,
                line.getId(),
                "PRODUCTION_LINE"
        );

        // 2. Envoyer le message système automatique
        String systemMsg = String.format(
                "📋 Vous avez été affecté(e) à la ligne de production \"%s\" (Code: %s).\n" +
                        "Affecté par : %s (%s).\n" +
                        "N'hésitez pas à poser vos questions ici.",
                line.getName(), line.getCode(),
                assignedBy.getUsername(), formatRole(assignedBy.getRole())
        );

        messageService.sendSystemMessage(
                conversation.getId(),
                systemMsg,
                MessageType.LINE_ASSIGNMENT,
                assignedBy.getId()
        );

        // 3. Envoyer une notification push
        notificationService.createAndSend(
                assignedUser.getId(),
                "Nouvelle affectation — Ligne " + line.getCode(),
                String.format("Vous êtes affecté(e) à la ligne \"%s\" par %s.",
                        line.getName(), assignedBy.getUsername()),
                NotificationType.LINE_ASSIGNED,
                line.getId(),
                "PRODUCTION_LINE"
        );
    }

    /**
     * Appelé quand une validation est créée et affectée.
     * Le technicien reçoit les détails de la validation.
     */
    /*
    public void onValidationCreated(Validation validation, User assignedTech, User createdBy) {
        log.info("Événement: Validation #{} créée par {}, affectée à {}",
                validation.getId(), createdBy.getUsername(), assignedTech.getUsername());

        // 1. Créer la conversation liée à la validation
        Conversation conversation = messageService.createConversation(
                createdBy.getId(),
                assignedTech.getId(),
                ConversationType.VALIDATION_ASSIGNMENT,
                validation.getId(),
                "VALIDATION"
        );

        // 2. Message système avec détails de la validation
        String zoneName = validation.getValidationZone().getName();
        String lineName = validation.getValidationZone().getProductionLine().getName();

        String systemMsg = String.format(
                "🔬 Nouvelle validation à effectuer !\n\n" +
                        "📌 Validation #%d\n" +
                        "📍 Zone : %s\n" +
                        "🏭 Ligne : %s\n" +
                        "📝 Commentaires : %s\n" +
                        "📅 Date de début : %s\n\n" +
                        "Créée par : %s (%s)\n" +
                        "Vous pouvez discuter des détails ici.",
                validation.getId(),
                zoneName,
                lineName,
                validation.getComments() != null ? validation.getComments() : "Aucun",
                validation.getStartDate().toString(),
                createdBy.getUsername(),
                formatRole(createdBy.getRole())
        );

        messageService.sendSystemMessage(
                conversation.getId(),
                systemMsg,
                MessageType.VALIDATION_ASSIGNMENT,
                createdBy.getId()
        );

        // 3. Notification push
        notificationService.createAndSend(
                assignedTech.getId(),
                "Validation #" + validation.getId() + " assignée",
                String.format("Nouvelle validation dans la zone \"%s\" (Ligne %s). Créée par %s.",
                        zoneName, lineName, createdBy.getUsername()),
                NotificationType.VALIDATION_ASSIGNED,
                validation.getId(),
                "VALIDATION"
        );
    }
    */

    /**
     * Appelé quand l'IA détecte un risque élevé.
     */
    public void onAIAlert(Validation validation, String riskLevel, double riskScore) {
        // Resolve the line via the new FK first, fall back to the legacy
        // zone→line chain for tickets that haven't been migrated yet.
        ProductionLine line = validation.getProductionLine();
        if (line == null && validation.getValidationZone() != null) {
            line = validation.getValidationZone().getProductionLine();
        }
        String lineName = line != null ? line.getName() : "?";
        String zoneName = validation.getValidationZone() != null
                ? validation.getValidationZone().getName() : "?";

        String content = String.format(
                "⚠️ Alerte IA — Validation #%d\n" +
                        "Niveau de risque : %s (Score: %.0f%%)\n" +
                        "Zone : %s | Ligne : %s",
                validation.getId(),
                riskLevel,
                riskScore * 100,
                zoneName,
                lineName
        );

        // On pourrait chercher le chef de secteur de la ligne ici
        // Pour simplifier, on notifie tous les chefs de secteur affectés
        log.info("Alerte IA envoyée pour validation #{}", validation.getId());
    }

    /**
     * Appelé quand une validation est clôturée.
     */
    public void onValidationClosed(Validation validation, User closedBy) {
        String statusLabel = validation.getStatus() == TicketStatus.CONFORME
                ? "✅ CONFORME" : "❌ NON CONFORME";

        log.info("Validation #{} clôturée par {} — Statut: {} ({})",
                validation.getId(), closedBy.getUsername(), validation.getStatus(), statusLabel);
    }

    // =====================================================
    // TICKET WORKFLOW EVENTS
    // =====================================================

    /**
     * Emit notification + VALIDATION_ASSIGNMENT auto-message for a freshly
     * persisted {@link ValidationAssignment}.
     *
     * <p><b>2026-04 line-ticket dedupe (M2 semantics):</b> a single line-ticket
     * can have several assignments for the same user (one per poste). We send
     * the detailed affectation message exactly once per (user, ticket) — on the
     * assignment whose id is the lowest. Subsequent calls still create the
     * tech's notification (so they see each new poste) but skip the auto-message
     * to keep the conversation panel clean.
     */
    public void onTicketAssignment(ValidationAssignment assignment) {
        Validation validation = assignment.getValidation();
        User user = assignment.getUser();
        if (user == null || validation == null) {
            return;
        }

        String roleName = assignment.getAssignmentRole() == AssignmentRole.TECH_PREPARATION
                ? "Technicien Préparation" : "Technicien Validation";

        // Gather this user's assignments on this ticket so we can (a) detect
        // duplicates and (b) list every poste they're responsible for in the
        // auto-message.
        java.util.List<ValidationAssignment> sameTicketForUser =
                assignmentRepository.findByValidationIdAndUserId(
                        validation.getId(), user.getId());

        boolean isFirstAssignmentForUser = sameTicketForUser.isEmpty()
                || sameTicketForUser.stream()
                        .allMatch(a -> a.getId() == null
                                || assignment.getId() == null
                                || a.getId() >= assignment.getId());

        // Poste list attached to this user on this ticket
        String postesLabel = sameTicketForUser.isEmpty()
                ? (assignment.getZone() != null ? assignment.getZone().getName() : "?")
                : sameTicketForUser.stream()
                        .map(a -> a.getZone() != null ? a.getZone().getName() : "?")
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("?");

        // Line + sector info (prefer the new direct FK, fall back to the legacy chain)
        String lineName = "?";
        String lineCode = "?";
        String secteurName = "?";
        try {
            ProductionLine line = validation.getProductionLine();
            if (line == null && validation.getValidationZone() != null) {
                line = validation.getValidationZone().getProductionLine();
            }
            if (line != null) {
                lineName = line.getName();
                lineCode = line.getCode();
                if (line.getPhase() != null && line.getPhase().getSecteur() != null) {
                    secteurName = line.getPhase().getSecteur().getName();
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger les détails ligne/secteur: {}", e.getMessage());
        }

        String priority = validation.getPriority() != null ? validation.getPriority().name() : "NORMALE";
        String plannedDate = validation.getPlannedDate() != null
                ? validation.getPlannedDate().toString() : "Non définie";
        String comments = validation.getComments() != null && !validation.getComments().isBlank()
                ? validation.getComments() : "Aucun";
        String assignerName = validation.getCreatedBy() != null
                ? validation.getCreatedBy().getUsername() : "Système";
        String assignerRole = validation.getCreatedBy() != null
                ? formatRole(validation.getCreatedBy().getRole()) : "";

        // 1. Notification push — always fired so the tech sees every poste they
        //    get attached to, even if the conversation is already open.
        String title = isFirstAssignmentForUser
                ? "Nouvelle affectation — Ticket " + validation.getTicketCode()
                : "Poste ajouté — Ticket " + validation.getTicketCode();
        String notifContent = isFirstAssignmentForUser
                ? String.format(
                        "Vous avez été affecté(e) au ticket %s (ligne %s) en tant que %s sur le(s) poste(s) : %s. Priorité: %s",
                        validation.getTicketCode(), lineCode, roleName, postesLabel, priority)
                : String.format(
                        "Nouveau poste ajouté sur le ticket %s : %s (%s).",
                        validation.getTicketCode(),
                        assignment.getZone() != null ? assignment.getZone().getName() : "?",
                        roleName);

        notificationService.createAndSend(
                user.getId(),
                title,
                notifContent,
                NotificationType.TICKET_ASSIGNMENT,
                validation.getId(),
                "VALIDATION"
        );

        // 2. Auto-message — only on the FIRST assignment for this user on this
        //    ticket. For additional postes the existing conversation is reused
        //    silently (the tech will see the new poste in the ticket detail).
        if (isFirstAssignmentForUser
                && validation.getCreatedBy() != null
                && !validation.getCreatedBy().getId().equals(user.getId())) {
            try {
                String autoMsg = String.format(
                        "📋 Nouvelle affectation — Ticket %s\n\n" +
                        "👤 Rôle : %s\n" +
                        "🏭 Ligne : %s (%s)\n" +
                        "🔧 Secteur : %s\n" +
                        "📍 Poste(s) : %s\n" +
                        "⚡ Priorité : %s\n" +
                        "📅 Date planifiée : %s\n" +
                        "💬 Commentaires : %s\n\n" +
                        "Affecté par : %s (%s)\n" +
                        "N'hésitez pas à poser vos questions ici.",
                        validation.getTicketCode(),
                        roleName,
                        lineName, lineCode,
                        secteurName,
                        postesLabel,
                        priority,
                        plannedDate,
                        comments,
                        assignerName,
                        assignerRole
                );
                messageService.sendAutoMessageForValidation(
                        validation.getCreatedBy().getId(),
                        user.getId(),
                        autoMsg,
                        validation.getId()
                );

                // Notify the creator (chef secteur) so their conversation list
                // refreshes in real-time (the tech user is already notified above).
                notificationService.createAndSend(
                        validation.getCreatedBy().getId(),
                        "Conversation ouverte — Ticket " + validation.getTicketCode(),
                        String.format("Une conversation a été créée avec %s (%s) pour le ticket %s.",
                                user.getUsername(), roleName, validation.getTicketCode()),
                        NotificationType.TICKET_ASSIGNMENT,
                        validation.getId(),
                        "VALIDATION"
                );
            } catch (Exception e) {
                log.warn("Auto-message échoué pour user {}: {}", user.getId(), e.getMessage(), e);
            }
        } else if (!isFirstAssignmentForUser) {
            log.info("Ticket {} — affectation supplémentaire pour {} (poste {}), pas de nouveau message (M2 dedupe)",
                    validation.getTicketCode(),
                    user.getUsername(),
                    assignment.getZone() != null ? assignment.getZone().getName() : "?");
        }

        log.info("Ticket {} affecté à {} ({})",
                validation.getTicketCode(), user.getUsername(), assignment.getAssignmentRole());
    }

    public void onTicketStatusChange(Validation validation, TicketStatus previous, User actor) {
        String title = "Ticket " + validation.getTicketCode() + " — " + validation.getStatus();
        String content = String.format(
                "Statut changé de %s → %s par %s.",
                previous, validation.getStatus(),
                actor != null ? actor.getUsername() : "système"
        );

        if (validation.getAssignments() != null) {
            validation.getAssignments().forEach(a -> {
                if (a.getUser() != null) {
                    notificationService.createAndSend(
                            a.getUser().getId(),
                            title, content,
                            NotificationType.TICKET_STATUS_CHANGE,
                            validation.getId(),
                            "VALIDATION"
                    );
                }
            });
        }

        log.info("Ticket {} : {} → {}", validation.getTicketCode(), previous, validation.getStatus());
    }

    public void onPrepValidated(Validation validation, User techPrep) {
        String title = "Outillage validé — Ticket " + validation.getTicketCode();
        String content = String.format(
                "L'outillage du ticket %s a été vérifié et validé par %s.",
                validation.getTicketCode(),
                techPrep != null ? techPrep.getUsername() : "TECH_PREP"
        );

        if (validation.getAssignments() != null) {
            validation.getAssignments().stream()
                    .filter(a -> a.getAssignmentRole() == AssignmentRole.TECH_VALIDATION)
                    .forEach(a -> {
                        if (a.getUser() != null) {
                            notificationService.createAndSend(
                                    a.getUser().getId(),
                                    title, content,
                                    NotificationType.PREP_VALIDATED,
                                    validation.getId(),
                                    "VALIDATION"
                            );
                        }
                    });
        }

        log.info("Outillage validé pour ticket {} par {}",
                validation.getTicketCode(),
                techPrep != null ? techPrep.getUsername() : "?");
    }

    public void onTicketSubmittedForReview(Validation validation, User submittedBy) {
        String title = "Ticket en revue — " + validation.getTicketCode();
        String content = String.format(
                "Le ticket %s a été soumis pour revue par %s.",
                validation.getTicketCode(),
                submittedBy != null ? submittedBy.getUsername() : "technicien"
        );

        User creator = validation.getCreatedBy();
        if (creator != null) {
            notificationService.createAndSend(
                    creator.getId(),
                    title, content,
                    NotificationType.TICKET_REVIEW,
                    validation.getId(),
                    "VALIDATION"
            );
        }

        log.info("Ticket {} soumis en revue par {}",
                validation.getTicketCode(),
                submittedBy != null ? submittedBy.getUsername() : "?");
    }

    public void onTicketCancelled(Validation validation, User cancelledBy, String reason) {
        String title = "Ticket annulé — " + validation.getTicketCode();
        String content = String.format(
                "Le ticket %s a été annulé par %s.%s",
                validation.getTicketCode(),
                cancelledBy != null ? cancelledBy.getUsername() : "système",
                reason != null && !reason.isBlank() ? " Raison: " + reason : ""
        );

        if (validation.getAssignments() != null) {
            validation.getAssignments().forEach(a -> {
                if (a.getUser() != null) {
                    notificationService.createAndSend(
                            a.getUser().getId(),
                            title, content,
                            NotificationType.TICKET_CANCELLED,
                            validation.getId(),
                            "VALIDATION"
                    );
                }
            });
        }

        log.info("Ticket {} annulé par {}",
                validation.getTicketCode(),
                cancelledBy != null ? cancelledBy.getUsername() : "?");
    }

    private String formatRole(Role role) {
        switch (role) {
            case ADMIN_IT: return "Administrateur IT";
            case CHEF_SECTEUR: return "Chef de Secteur";
            case EXPERT: return "Expert";
            case TECH_VAL: return "Technicien Validation";
            case TECH_PREP: return "Technicien Préparation";
            case RESPONSABLE: return "Responsable";
            default: return role.name();
        }
    }
}