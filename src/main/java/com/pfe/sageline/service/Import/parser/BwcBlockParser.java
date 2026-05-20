package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.SourceDeclaredStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the real Sagemcom BWC (Banc WiFi Conduit) log emitted by the IQXEL
 * tester. The file is organised into numbered test sections; result lines
 * inside each section follow the IQXEL shape:
 *
 * <pre>
 * &lt;N&gt;.&lt;SECTION_NAME&gt;  _________________________________________________
 * ...
 * KEY              :  &lt;value&gt; [unit]   ( &lt;lo&gt;, &lt;hi&gt; )
 * </pre>
 *
 * Each (section, key) pair becomes one ParsedMeasure with code
 * {@code SECTION_SANITIZED__KEY}. Empty bounds {@code (,)} mean the row is
 * informational (no station-side judgment).
 */
public final class BwcBlockParser {

    private static final Pattern SECTION = Pattern.compile(
            "^\\s*\\d+\\.\\s*(?<name>.+?)\\s*_{2,}\\s*$",
            Pattern.MULTILINE
    );

    private static final Pattern MEASURE_LINE = Pattern.compile(
            "^\\s*(?<key>[A-Z][A-Z0-9_]*)\\s*:\\s*"
                    + "(?<value>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*"
                    + "(?<unit>[^\\s(]*)\\s*"
                    + "\\(\\s*(?<lo>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)?\\s*,"
                    + "\\s*(?<hi>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)?\\s*\\)\\s*$",
            Pattern.MULTILINE
    );

    private BwcBlockParser() {}

    private record Section(String name, int matchStart, int matchEnd) {}

    public static List<ParsedMeasure> parseBlocks(String content, List<String> parserNotes) {
        List<ParsedMeasure> measures = new ArrayList<>();

        Matcher sm = SECTION.matcher(content);
        List<Section> sections = new ArrayList<>();
        while (sm.find()) {
            sections.add(new Section(sm.group("name").trim(), sm.start(), sm.end()));
        }
        if (sections.isEmpty()) {
            return measures;
        }

        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            String sectionName = s.name();
            String sectionCode = sanitize(sectionName);
            int bodyStart = s.matchEnd();
            int bodyEnd = (i + 1 < sections.size()) ? sections.get(i + 1).matchStart() : content.length();
            String body = content.substring(bodyStart, bodyEnd);

            Matcher mm = MEASURE_LINE.matcher(body);
            while (mm.find()) {
                String key = mm.group("key");
                String code = sectionCode + "__" + key;

                Double value;
                try {
                    value = Double.parseDouble(mm.group("value"));
                } catch (NumberFormatException e) {
                    parserNotes.add("BWC: malformed value for " + code);
                    continue;
                }

                Double lo = parseOpt(mm.group("lo"));
                Double hi = parseOpt(mm.group("hi"));

                String unit = mm.group("unit");
                if (unit != null && unit.isEmpty()) {
                    unit = null;
                }

                // Derive source-declared status from the station's bounds when
                // available. If neither bound is parsed the row is informational.
                SourceDeclaredStatus src;
                if (lo == null && hi == null) {
                    src = SourceDeclaredStatus.NOT_EXECUTED_FROM_LOG;
                } else {
                    boolean below = lo != null && value < lo;
                    boolean above = hi != null && value > hi;
                    src = (below || above)
                            ? SourceDeclaredStatus.OUT_OF_RANGE_FROM_LOG
                            : SourceDeclaredStatus.OK_FROM_LOG;
                }

                String label = sectionName + " / " + key;
                measures.add(new ParsedMeasure(code, label, src, lo, hi, unit, value));
            }
        }
        return measures;
    }

    private static Double parseOpt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s.replaceAll("\\s+", "_").replaceAll("_+$", "");
    }
}
