package com.pfe.sageline.service.workflow;

import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;
import com.pfe.sageline.enums.TicketStatus;
import com.pfe.sageline.exception.TransitionBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketTransitionGuard {

    private final WorkflowReadinessService readinessService;

    public void check(Long validationId, TicketStatus targetStatus) {
        WorkflowReadinessDTO readiness = readinessService.computeReadiness(validationId, targetStatus);
        if (!readiness.canTransition()) throw new TransitionBlockedException(readiness);
    }
}
