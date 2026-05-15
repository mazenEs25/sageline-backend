package com.pfe.sageline.exception;

import java.util.List;

public class LogParseException extends RuntimeException {
    private final List<String> parserNotes;

    public LogParseException(String message, List<String> parserNotes) {
        super(message);
        this.parserNotes = parserNotes;
    }

    public List<String> getParserNotes() {
        return parserNotes;
    }
}
