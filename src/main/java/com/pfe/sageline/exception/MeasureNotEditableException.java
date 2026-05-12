package com.pfe.sageline.exception;

import com.pfe.sageline.enums.TicketStatus;
import lombok.Getter;

@Getter
public class MeasureNotEditableException extends RuntimeException {

    private final Long ticketId;
    private final TicketStatus currentStatus;

    public MeasureNotEditableException(Long ticketId, TicketStatus currentStatus) {
        super(String.format(
                "Ticket %d is in status %s; measures are read-only outside EN_COURS",
                ticketId, currentStatus));
        this.ticketId = ticketId;
        this.currentStatus = currentStatus;
    }
}
