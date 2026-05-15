package com.pfe.sageline.exception;

public class UnsupportedLogFormatException extends RuntimeException {
    private final String headerSample;

    public UnsupportedLogFormatException(String message, String headerSample) {
        super(message);
        this.headerSample = headerSample;
    }

    public String getHeaderSample() {
        return headerSample;
    }
}
