package com.pfe.sageline.enums;

public enum NotificationType {
    LINE_ASSIGNED,
    VALIDATION_ASSIGNED,
    VALIDATION_CLOSED,
    AI_ALERT,
    NEW_MESSAGE,
    SYSTEM,
    // Ticket workflow events
    TICKET_ASSIGNMENT,
    TICKET_STATUS_CHANGE,
    PREP_VALIDATED,
    TICKET_REVIEW,
    TICKET_CANCELLED
}
