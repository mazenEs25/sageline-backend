package com.pfe.sageline.service.workflow;

import com.pfe.sageline.entity.Validation;
import com.pfe.sageline.enums.TicketStatus;

public interface TransitionRule {
    RuleVerdict evaluate(Validation ticket, TicketStatus targetStatus);
}
