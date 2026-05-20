package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.SourceDeclaredStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the real Sagemcom BNFT supervisor log "Mesure" summary block that
 * appears near the end of a BNFT run. Per-measure shape:
 *
 * <pre>
 * Mesure &lt;CODE&gt; : &lt;label&gt; - Status &lt;0|1|2&gt;
 *                 &lt;min&gt; [unit] &lt; ... &lt; &lt;max&gt; [unit]
 *                 &lt;value&gt; [unit]      (absent when Status = 2)
 * </pre>
 *
 * Status 0 = OK, 1 = OUT_OF_RANGE, 2 = NOT_EXECUTED (matches Sagemcom convention).
 */
public final class BnftBlockParser {

    private static final Pattern HEADER = Pattern.compile(
            "Mesure\\s+<(?<code>[^>]+)>\\s*:\\s*(?<label>.+?)\\s+-\\s+Status\\s+(?<status>[0-2])"
    );

    // Bounds: "<min> [unit] < ... < <max> [unit]" — unit may be missing.
    private static final Pattern BOUNDS = Pattern.compile(
            "(?<min>-?\\d+(?:\\.\\d+)?)\\s+(?:(?<unit>\\S+)\\s+)?<\\s*\\.\\.\\.\\s*<\\s*(?<max>-?\\d+(?:\\.\\d+)?)"
    );

    // Value line on its own line; unit optional.
    private static final Pattern VALUE = Pattern.compile(
            "^\\s*(?<value>-?\\d+(?:\\.\\d+)?)(?:\\s+(?<unit>\\S+))?\\s*$",
            Pattern.MULTILINE
    );

    private BnftBlockParser() {}

    private record Header(String code, String label, int statusInt, int matchStart, int matchEnd) {}

    public static List<ParsedMeasure> parseBlocks(String content, List<String> parserNotes) {
        List<ParsedMeasure> measures = new ArrayList<>();
        Matcher hdr = HEADER.matcher(content);

        List<Header> hits = new ArrayList<>();
        while (hdr.find()) {
            String code = hdr.group("code").trim();
            String label = hdr.group("label").trim();
            int statusInt;
            try {
                statusInt = Integer.parseInt(hdr.group("status"));
            } catch (NumberFormatException e) {
                parserNotes.add("BNFT: malformed status for " + code);
                continue;
            }
            hits.add(new Header(code, label, statusInt, hdr.start(), hdr.end()));
        }

        for (int i = 0; i < hits.size(); i++) {
            Header h = hits.get(i);
            String code = h.code();
            String label = h.label();
            int statusInt = h.statusInt();

            int chunkStart = h.matchEnd();
            int chunkEnd = (i + 1 < hits.size()) ? hits.get(i + 1).matchStart() : content.length();
            String chunk = content.substring(chunkStart, chunkEnd);

            Double min = null;
            Double max = null;
            String unit = null;
            int valueSearchFrom = 0;

            Matcher bm = BOUNDS.matcher(chunk);
            if (bm.find()) {
                try {
                    min = Double.parseDouble(bm.group("min"));
                    max = Double.parseDouble(bm.group("max"));
                    unit = bm.group("unit");
                    valueSearchFrom = bm.end();
                } catch (NumberFormatException e) {
                    parserNotes.add("BNFT: malformed bounds for " + code);
                }
            }

            Double value = null;
            if (statusInt != 2) {
                String tail = chunk.substring(valueSearchFrom);
                Matcher vm = VALUE.matcher(tail);
                if (vm.find()) {
                    try {
                        value = Double.parseDouble(vm.group("value"));
                        if (unit == null) {
                            unit = vm.group("unit");
                        }
                    } catch (NumberFormatException e) {
                        parserNotes.add("BNFT: malformed value for " + code);
                    }
                }
            }

            SourceDeclaredStatus src;
            try {
                src = SourceDeclaredStatus.fromSagemcomStatus(statusInt);
            } catch (IllegalArgumentException e) {
                src = SourceDeclaredStatus.NOT_EXECUTED_FROM_LOG;
                parserNotes.add("BNFT: unknown status " + statusInt + " for " + code);
            }

            measures.add(new ParsedMeasure(code, label, src, min, max, unit, value));
        }
        return measures;
    }
}
