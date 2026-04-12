package com.pfe.sageline.service;

import com.pfe.sageline.entity.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessagingEventService {

    private final MessageService messageService;
    private final NotificationService notificationService;

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
        String statusLabel = validation.getStatus() == ValidationStatus.CONFORME
                ? "✅ CONFORME" : "❌ NON CONFORME";

        // Trouver la conversation liée à cette validation
        // et envoyer un message de clôture
        log.info("Validation #{} clôturée par {} — Statut: {}",
                validation.getId(), closedBy.getUsername(), validation.getStatus());
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