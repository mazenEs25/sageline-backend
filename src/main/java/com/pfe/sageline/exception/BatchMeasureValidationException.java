package com.pfe.sageline.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Getter
public class BatchMeasureValidationException extends RuntimeException {

    private final List<FailedEntry> failedEntries;
    private final int totalEntries;

    public BatchMeasureValidationException(int totalEntries, List<FailedEntry> failedEntries) {
        super("Batch measure validation failed");
        this.totalEntries = totalEntries;
        this.failedEntries = failedEntries;
    }

    @Data
    @AllArgsConstructor
    public static class FailedEntry {
        int index;
        String code;
        String message;
    }
}
