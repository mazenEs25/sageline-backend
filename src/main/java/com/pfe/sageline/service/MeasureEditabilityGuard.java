package com.pfe.sageline.service;

import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.MeasureNotEditableException;
import org.springframework.stereotype.Component;

@Component
public class MeasureEditabilityGuard {

    public void requireEditable(Validation ticket) {
        if (ticket.getStatus() != TicketStatus.EN_COURS) {
            throw new MeasureNotEditableException(ticket.getId(), ticket.getStatus());
        }
    }
}
