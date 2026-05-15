package com.pfe.sageline.service.Import.parser;

public interface LogFormatStrategy {
    boolean supports(String headerSample);
    ParsedLog parse(String content);
}
