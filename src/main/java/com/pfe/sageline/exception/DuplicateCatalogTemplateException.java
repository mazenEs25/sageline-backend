package com.pfe.sageline.exception;

import java.util.List;

public class DuplicateCatalogTemplateException extends RuntimeException {

    private final List<String> conflictingCodes;

    public DuplicateCatalogTemplateException(List<String> conflictingCodes) {
        super("Duplicate catalog template codes: " + conflictingCodes);
        this.conflictingCodes = conflictingCodes;
    }

    public List<String> getConflictingCodes() {
        return conflictingCodes;
    }
}
