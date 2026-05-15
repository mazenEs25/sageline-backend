package com.pfe.sageline.service.Import.parser;

import com.pfe.sageline.enums.SourceDeclaredStatus;

public record ParsedMeasure(
    String code,
    String label,
    SourceDeclaredStatus sourceStatus,
    Double lowerBound,
    Double upperBound,
    String unit,
    Double measuredValue
) {}
