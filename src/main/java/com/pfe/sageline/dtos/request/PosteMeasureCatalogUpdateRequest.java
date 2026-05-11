package com.pfe.sageline.dtos.request;

import com.pfe.sageline.enums.MeasureCategory;
import jakarta.validation.constraints.*;

/**
 * Partial-update DTO: every field is optional. posteType and measureCode are
 * intentionally absent — they are immutable per FR-011.
 *
 * Null fields are ignored by the service; non-null fields replace the current value.
 * Set {@code active=true} to reactivate a soft-deleted template.
 */
public record PosteMeasureCatalogUpdateRequest(
    @Size(min = 1, max = 255, message = "measureLabel must be between 1 and 255 characters")
    String measureLabel,

    MeasureCategory category,

    @Size(min = 1, max = 16, message = "defaultUnit must be between 1 and 16 characters")
    String defaultUnit,

    Double defaultLowerBound,

    Double defaultUpperBound,

    Boolean mandatory,

    @Min(value = 0, message = "displayOrder must be >= 0")
    Integer displayOrder,

    @Size(max = 16, message = "antenna must be at most 16 characters")
    String antenna,

    @Min(value = 0, message = "frequencyMhz must be non-negative")
    Integer frequencyMhz,

    @Size(max = 32, message = "modulationScheme must be at most 32 characters")
    String modulationScheme,

    Boolean active
) {
}
