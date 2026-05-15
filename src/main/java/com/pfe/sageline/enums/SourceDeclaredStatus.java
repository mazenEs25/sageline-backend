package com.pfe.sageline.enums;

public enum SourceDeclaredStatus {
    OK_FROM_LOG,
    OUT_OF_RANGE_FROM_LOG,
    NOT_EXECUTED_FROM_LOG;

    public static SourceDeclaredStatus fromSagemcomStatus(int status) {
        return switch (status) {
            case 0 -> OK_FROM_LOG;
            case 1 -> OUT_OF_RANGE_FROM_LOG;
            case 2 -> NOT_EXECUTED_FROM_LOG;
            default -> throw new IllegalArgumentException("Unknown Sagemcom status: " + status);
        };
    }
}
