package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.LogFormat;
import com.pfe.sageline.exception.LogParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class BnftLogStrategy implements LogFormatStrategy {

    private static final String HEADER_MARKER = "[TEST_FONCTIONNEL]";

    @Override
    public boolean supports(String headerSample) {
        return headerSample.contains(HEADER_MARKER);
    }

    @Override
    public ParsedLog parse(String content) {
        List<String> parserNotes = new ArrayList<>();
        List<ParsedMeasure> measures = BlockFormatParser.parseBlocks(content, parserNotes);
        if (measures.isEmpty()) {
            throw new LogParseException("No valid measure blocks found in BNFT log file", parserNotes);
        }
        log.debug("Parsed {} measures from BNFT log", measures.size());
        return new ParsedLog(LogFormat.BNFT, measures, parserNotes);
    }
}
