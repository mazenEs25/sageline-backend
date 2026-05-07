package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.HandoverStatus;
import com.pfe.sageline.enums.TriggerType;

import java.time.LocalDateTime;

public record HandoverResponse(
        Long id,
        Long validationId,
        String ticketCode,
        String fromTechUsername,
        String toTechUsername,
        String handoverNote,
        String progressSummary,
        HandoverStatus status,
        TriggerType triggeredBy,
        LocalDateTime scheduledAt,
        LocalDateTime acceptedAt
) {}
