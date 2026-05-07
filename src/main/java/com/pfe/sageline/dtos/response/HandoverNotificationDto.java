package com.pfe.sageline.dtos.response;

import java.time.LocalDateTime;

public record HandoverNotificationDto(
        String type,
        Long handoverId,
        Long validationId,
        String ticketCode,
        String message,
        LocalDateTime timestamp
) {}
