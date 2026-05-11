package com.pfe.sageline.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class BatchValidationException extends RuntimeException {

    private final List<BatchValidationError> errors;

    public BatchValidationException(List<BatchValidationError> errors) {
        super("Batch validation failed with " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<BatchValidationError> getErrors() {
        return errors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchValidationError {
        private int index;
        private String field;
        private String code;
        private String message;
    }
}
