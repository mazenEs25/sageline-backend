package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.LogFormat;
import com.pfe.sageline.exception.LogParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class BwcLogStrategy implements LogFormatStrategy {

    // Real Sagemcom BWC logs are identified by the GTW_BWC config-key prefix,
    // the BWC_V version string, or the ATR_TEST_WIFI_CONDUIT operation name.
    private static final String MARKER_CONF = "conf_GTW_BWC";
    private static final String MARKER_VERSION = "BWC_V";
    private static final String MARKER_OPERATION = "ATR_TEST_WIFI_CONDUIT";

    @Override
    public boolean supports(String headerSample) {
        return headerSample.contains(MARKER_CONF)
                || headerSample.contains(MARKER_VERSION)
                || headerSample.contains(MARKER_OPERATION);
    }

    @Override
    public ParsedLog parse(String content) {
        List<String> parserNotes = new ArrayList<>();
        List<ParsedMeasure> measures = BwcBlockParser.parseBlocks(content, parserNotes);
        if (measures.isEmpty()) {
            throw new LogParseException("No valid measure blocks found in BWC log file", parserNotes);
        }
        log.debug("Parsed {} measures from BWC log", measures.size());
        return new ParsedLog(LogFormat.BWC, measures, parserNotes);
    }
}
