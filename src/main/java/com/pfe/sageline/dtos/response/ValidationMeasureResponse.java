package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.MeasureStatus;

import java.time.Instant;

public record ValidationMeasureResponse(
        Long id,
        Long validationId,
        Long catalogTemplateId,
        String measureCode,
        String measureLabel,
        MeasureCategory category,
        String unit,
        Double lowerBound,
        Double upperBound,
        Double measuredValue,
        MeasureStatus status,
        Double deviationPct,
        String antenna,
        Integer frequencyMhz,
        String modulationScheme,
        String sourceLogFile,
        Long enteredById,
        String enteredByUsername,
        Instant measuredAt,
        Instant createdAt,
        Instant updatedAt
) {}
