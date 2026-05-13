package com.pfe.sageline.exception;

import com.pfe.sageline.dtos.response.WorkflowReadinessDTO;

public class TransitionBlockedException extends RuntimeException {
    private final transient WorkflowReadinessDTO readiness;

    public TransitionBlockedException(WorkflowReadinessDTO readiness) {
        super("Transition blocked: " + String.join("; ", readiness.blockingReasons()));
        this.readiness = readiness;
    }

    public WorkflowReadinessDTO getReadiness() { return readiness; }
}
