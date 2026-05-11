package com.pfe.sageline.exception;

public class BoundsViolationException extends RuntimeException {

    private final String reason;

    public BoundsViolationException(String reason) {
        super("Bounds violation: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
