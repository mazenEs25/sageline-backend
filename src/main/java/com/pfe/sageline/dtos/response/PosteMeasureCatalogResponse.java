package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;

import java.time.Instant;

public record PosteMeasureCatalogResponse(
    Long id,
    PosteType posteType,
    String measureCode,
    String measureLabel,
    MeasureCategory category,
    String defaultUnit,
    Double defaultLowerBound,
    Double defaultUpperBound,
    Boolean mandatory,
    Integer displayOrder,
    Boolean active,
    String antenna,
    Integer frequencyMhz,
    String modulationScheme,
    Instant createdAt,
    Long createdBy,
    Instant updatedAt,
    Long updatedBy
) {
}
