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

    // Real Sagemcom BNFT logs are identified by the DEC_BNFT config-key prefix
    // or the NFT_V version string that appear in the early header.
    private static final String MARKER_CONF = "conf_DEC_BNFT";
    private static final String MARKER_VERSION = "NFT_V";

    @Override
    public boolean supports(String headerSample) {
        return headerSample.contains(MARKER_CONF) || headerSample.contains(MARKER_VERSION);
    }

    @Override
    public ParsedLog parse(String content) {
        List<String> parserNotes = new ArrayList<>();
        List<ParsedMeasure> measures = BnftBlockParser.parseBlocks(content, parserNotes);
        if (measures.isEmpty()) {
            throw new LogParseException("No valid measure blocks found in BNFT log file", parserNotes);
        }
        log.debug("Parsed {} measures from BNFT log", measures.size());
        return new ParsedLog(LogFormat.BNFT, measures, parserNotes);
    }
}
