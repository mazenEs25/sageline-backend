package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.MeasureStatus;

import java.time.Instant;

/**
 * Response shape for a {@code ValidationMeasure}.
 *
 * <p>{@code posteStatusId} was added in the V5.0 schema migration so frontend
 * components can scope rows to a specific poste of the line (per-poste UI).
 * Null is possible only for legacy ad-hoc rows that couldn't be backfilled.</p>
 */
public record ValidationMeasureResponse(
        Long id,
        Long validationId,
        Long posteStatusId,
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
