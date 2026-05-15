package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.exception.UnsupportedLogFormatException;
import com.pfe.sageline.service.Import.parser.LogFormatStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class HeaderSniffer {

    private final List<LogFormatStrategy> strategies;

    public HeaderSniffer(List<LogFormatStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Sniffs the log header and returns the appropriate parser strategy.
     * Reads the first 4096 characters to determine the format.
     *
     * @param content the log file content
     * @return the matching LogFormatStrategy
     * @throws UnsupportedLogFormatException if no strategy matches
     */
    public LogFormatStrategy sniff(String content) {
        int sniffSize = Math.min(4096, content.length());
        String headerSample = content.substring(0, sniffSize);

        for (LogFormatStrategy strategy : strategies) {
            if (strategy.supports(headerSample)) {
                log.debug("Matched log format strategy: {}", strategy.getClass().getSimpleName());
                return strategy;
            }
        }

        log.warn("No matching log format strategy found");
        throw new UnsupportedLogFormatException(
                "No supported log format detected in the uploaded file",
                headerSample
        );
    }
}
