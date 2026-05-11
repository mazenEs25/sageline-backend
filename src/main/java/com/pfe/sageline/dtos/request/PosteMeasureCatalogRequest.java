package com.pfe.sageline.dtos.request;

import com.pfe.sageline.enums.MeasureCategory;
import com.pfe.sageline.enums.PosteType;
import jakarta.validation.constraints.*;

public record PosteMeasureCatalogRequest(
    @NotNull(message = "posteType is required")
    PosteType posteType,

    @NotNull(message = "measureCode is required")
    @NotBlank(message = "measureCode cannot be blank")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "measureCode must start with uppercase letter and contain only uppercase letters, digits, and underscores")
    String measureCode,

    @NotNull(message = "measureLabel is required")
    @NotBlank(message = "measureLabel cannot be blank")
    @Size(min = 1, max = 255, message = "measureLabel must be between 1 and 255 characters")
    String measureLabel,

    @NotNull(message = "category is required")
    MeasureCategory category,

    @NotNull(message = "defaultUnit is required")
    @NotBlank(message = "defaultUnit cannot be blank")
    @Size(min = 1, max = 16, message = "defaultUnit must be between 1 and 16 characters")
    String defaultUnit,

    @NotNull(message = "defaultLowerBound is required")
    Double defaultLowerBound,

    @NotNull(message = "defaultUpperBound is required")
    Double defaultUpperBound,

    @NotNull(message = "mandatory is required")
    Boolean mandatory,

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be >= 0")
    Integer displayOrder,

    @Size(max = 16, message = "antenna must be at most 16 characters")
    String antenna,

    @Min(value = 0, message = "frequencyMhz must be non-negative")
    Integer frequencyMhz,

    @Size(max = 32, message = "modulationScheme must be at most 32 characters")
    String modulationScheme
) {
}
