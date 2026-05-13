package com.pfe.sageline.service.workflow;

import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TicketStatus;
import org.springframework.stereotype.Component;

@Component
public class SourceStatusRule implements TransitionRule {

    @Override
    public RuleVerdict evaluate(Validation ticket, TicketStatus targetStatus) {
        if (targetStatus != TicketStatus.EN_REVUE) {
            return RuleVerdict.allow();
        }
        if (ticket.getStatus() == TicketStatus.EN_COURS) {
            return RuleVerdict.allow();
        }
        return RuleVerdict.block("Ticket source status " + ticket.getStatus()
                + " is not eligible for transition to " + targetStatus);
    }
}
