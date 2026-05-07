package com.pfe.sageline.mappers;

import com.pfe.sageline.dtos.response.HandoverResponse;
import com.pfe.sageline.entity.TicketHandover;
import org.springframework.stereotype.Component;

@Component
public class HandoverMapper {

    public HandoverResponse toResponse(TicketHandover h) {
        return new HandoverResponse(
                h.getId(),
                h.getValidation() != null ? h.getValidation().getId() : null,
                h.getValidation() != null ? h.getValidation().getTicketCode() : null,
                h.getFromTech() != null ? h.getFromTech().getUsername() : null,
                h.getToTech() != null ? h.getToTech().getUsername() : null,
                h.getHandoverNote(),
                h.getProgressSummary(),
                h.getStatus(),
                h.getTriggeredBy(),
                h.getScheduledAt(),
                h.getAcceptedAt()
        );
    }
}
