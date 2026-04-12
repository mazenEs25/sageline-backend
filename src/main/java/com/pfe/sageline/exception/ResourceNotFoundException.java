package com.pfe.sageline.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceNotFoundException(String resource, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resource, fieldName, fieldValue));
    }
}