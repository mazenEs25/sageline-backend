package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.LogFormat;
import java.util.List;

public record ParsedLog(
    LogFormat format,
    List<ParsedMeasure> measures,
    List<String> parserNotes
) {}
