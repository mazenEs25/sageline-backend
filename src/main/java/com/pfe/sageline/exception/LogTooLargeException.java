package com.pfe.sageline.exception;

public class LogTooLargeException extends RuntimeException {
    private final long actualBytes;
    private final long limitBytes;

    public LogTooLargeException(String message, long actualBytes, long limitBytes) {
        super(message);
        this.actualBytes = actualBytes;
        this.limitBytes = limitBytes;
    }

    public long getActualBytes() {
        return actualBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }
}
