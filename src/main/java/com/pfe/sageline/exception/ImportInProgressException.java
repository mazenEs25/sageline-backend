package com.pfe.sageline.exception;

public class ImportInProgressException extends RuntimeException {
    private final Long validationId;

    public ImportInProgressException(String message, Long validationId) {
        super(message);
        this.validationId = validationId;
    }

    public Long getValidationId() {
        return validationId;
    }
}
