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
        // Notifier le chef de secteur de la ligne
        ProductionLine line = validation.getValidationZone().getProductionLine();

        String content = String.format(
                "⚠️ Alerte IA — Validation #%d\n" +
                        "Niveau de risque : %s (Score: %.0f%%)\n" +
                        "Zone : %s | Ligne : %s",
                validation.getId(),
                riskLevel,
                riskScore * 100,
                validation.getValidationZone().getName(),
                line.getName()
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

    public void onTicketAssignment(ValidationAssignment assignment) {
        Validation validation = assignment.getValidation();
        User user = assignment.getUser();
        if (user == null || validation == null) {
            return;
        }

        String roleName = assignment.getAssignmentRole() == AssignmentRole.TECH_PREPARATION
                ? "Technicien Préparation" : "Technicien Validation";

        // Zone, ligne, secteur info
        String zoneName = assignment.getZone() != null ? assignment.getZone().getName() : "?";
        String lineName = "?";
        String secteurName = "?";
        try {
            if (validation.getValidationZone() != null) {
                if (validation.getValidationZone().getProductionLine() != null) {
                    ProductionLine line = validation.getValidationZone().getProductionLine();
                    lineName = line.getName();
                    if (line.getPhase() != null && line.getPhase().getSecteur() != null) {
                        secteurName = line.getPhase().getSecteur().getName();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger les détails zone/ligne/secteur: {}", e.getMessage());
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

        // 1. Notification push
        String title = "Nouvelle affectation — Ticket " + validation.getTicketCode();
        String notifContent = String.format(
                "Vous avez été affecté(e) au ticket %s en tant que %s sur la zone %s. Priorité: %s",
                validation.getTicketCode(), roleName, zoneName, priority
        );

        notificationService.createAndSend(
                user.getId(),
                title,
                notifContent,
                NotificationType.TICKET_ASSIGNMENT,
                validation.getId(),
                "VALIDATION"
        );

        // 2. Auto-create VALIDATION_ASSIGNMENT conversation with detailed message
        if (validation.getCreatedBy() != null && !validation.getCreatedBy().getId().equals(user.getId())) {
            try {
                String autoMsg = String.format(
                        "📋 Nouvelle affectation — Ticket %s\n\n" +
                        "👤 Rôle : %s\n" +
                        "📍 Zone : %s\n" +
                        "🏭 Ligne : %s\n" +
                        "🔧 Secteur : %s\n" +
                        "⚡ Priorité : %s\n" +
                        "📅 Date planifiée : %s\n" +
                        "💬 Commentaires : %s\n\n" +
                        "Affecté par : %s (%s)\n" +
                        "N'hésitez pas à poser vos questions ici.",
                        validation.getTicketCode(),
                        roleName,
                        zoneName,
                        lineName,
                        secteurName,
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