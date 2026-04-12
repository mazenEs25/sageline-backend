package com.pfe.sageline.entity;

public enum MessageType {
    TEXT,                  // Message texte normal
    SYSTEM_NOTIFICATION,   // Notification système auto-générée
    VALIDATION_ALERT,      // Alerte IA sur validation
    LINE_ASSIGNMENT,       // Notification d'affectation ligne
    VALIDATION_ASSIGNMENT  // Notification d'affectation validation
}