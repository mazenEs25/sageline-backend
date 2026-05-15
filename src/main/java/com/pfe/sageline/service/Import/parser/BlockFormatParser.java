package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.SourceDeclaredStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared block-format parser for Sagemcom supervisor logs. The three formats
 * (BNFT, BWC, BTF) all share the same per-measure block:
 *
 * <pre>
 * Measure: &lt;CODE&gt;
 * Label: &lt;text&gt;
 * Category: &lt;CAT&gt;
 * Unit: &lt;unit&gt;
 * [Min: &lt;min&gt;, Max: &lt;max&gt;]      (optional — informational measures omit it)
 * [Status: &lt;0|1|2&gt; (text)]      (optional — present only when Min/Max are)
 * Measured Value: &lt;value&gt;
 * </pre>
 *
 * Measures without Min/Max are returned with {@code lowerBound=null} and
 * {@code upperBound=null}; downstream callers should fall back to catalog bounds.
 * The source status defaults to NOT_EXECUTED_FROM_LOG when the Status line is absent.
 */
public final class BlockFormatParser {

    private static final Pattern BLOCK = Pattern.compile(
            "Measure:\\s+(?<code>\\S+)\\s*\\R" +
            "Label:\\s+(?<label>.+?)\\s*\\R" +
            "Category:\\s+\\S+\\s*\\R" +
            "Unit:\\s+(?<unit>\\S+)\\s*\\R" +
            "(?:Min:\\s+(?<min>-?[\\d.]+)\\s*,\\s*Max:\\s+(?<max>-?[\\d.]+)\\s*\\R" +
            "Status:\\s+(?<status>\\d)\\s*\\([^)]*\\)\\s*\\R)?" +
            "Measured\\s+Value:\\s+(?<value>-?[\\d.]+)",
            Pattern.MULTILINE
    );

    private BlockFormatParser() {}

    /**
     * Parses every measure block found in the content. Order of returned entries
     * follows the file order. Malformed numeric tokens are logged as parser notes
     * (not thrown) so a single bad row doesn't sink the whole import.
     */
    public static List<ParsedMeasure> parseBlocks(String content, List<String> parserNotes) {
        List<ParsedMeasure> measures = new ArrayList<>();
        Matcher m = BLOCK.matcher(content);
        while (m.find()) {
            try {
                String code = m.group("code");
                String label = m.group("label").trim();
                String unit = m.group("unit");
                String minStr = m.group("min");
                String maxStr = m.group("max");
                String statusStr = m.group("status");
                String valueStr = m.group("value");

                Double min = minStr != null ? Double.parseDouble(minStr) : null;
                Double max = maxStr != null ? Double.parseDouble(maxStr) : null;
                SourceDeclaredStatus sourceStatus = statusStr != null
                        ? SourceDeclaredStatus.fromSagemcomStatus(Integer.parseInt(statusStr))
                        : SourceDeclaredStatus.NOT_EXECUTED_FROM_LOG;
                Double value = valueStr != null ? Double.parseDouble(valueStr) : null;

                measures.add(new ParsedMeasure(code, label, sourceStatus, min, max, unit, value));
            } catch (IllegalArgumentException e) {
                parserNotes.add("Skipped malformed measure block: " + e.getMessage());
            }
        }
        return measures;
    }
}
