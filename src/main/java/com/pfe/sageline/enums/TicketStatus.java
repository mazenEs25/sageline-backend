package com.pfe.sageline.enums;

public enum TicketStatus {
    PLANIFIE,           // Ticket créé et planifié
    EN_ATTENTE_PREP,    // En attente de vérification outillage par TECH_PREP
    PREP_VALIDEE,       // Outillage vérifié, prêt pour validation
    EN_COURS,           // TECH_VAL effectue la validation
    EN_REVUE,           // En attente de revue par CHEF_SECTEUR ou EXPERT
    CONFORME,           // Validation terminée — conforme
    NON_CONFORME,       // Validation terminée — non conforme
    ANNULE              // Ticket annulé
}