package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.LogFormat;
import com.pfe.sageline.exception.LogParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class BtfLogStrategy implements LogFormatStrategy {

    // Real Sagemcom BTF (Banc Test Fonctionnel) logs are identified by the
    // GTW_BTF config-key prefix or the TF_V version string in the early header.
    private static final String MARKER_CONF = "conf_GTW_BTF";
    private static final String MARKER_VERSION = "TF_V";

    @Override
    public boolean supports(String headerSample) {
        return headerSample.contains(MARKER_CONF) || headerSample.contains(MARKER_VERSION);
    }

    @Override
    public ParsedLog parse(String content) {
        List<String> parserNotes = new ArrayList<>();
        // BTF uses the same "Mesure <CODE> : <label> - Status N" summary block
        // as BNFT — same parser, different LogFormat tag for downstream routing.
        List<ParsedMeasure> measures = BnftBlockParser.parseBlocks(content, parserNotes);
        if (measures.isEmpty()) {
            throw new LogParseException("No valid measure blocks found in BTF log file", parserNotes);
        }
        log.debug("Parsed {} measures from BTF log", measures.size());
        return new ParsedLog(LogFormat.BTF, measures, parserNotes);
    }
}
