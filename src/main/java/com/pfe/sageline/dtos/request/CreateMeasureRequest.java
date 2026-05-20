package com.pfe.sageline.dtos.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pfe.sageline.enums.MeasureCategory;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a {@code ValidationMeasure}.
 *
 * <p><strong>Three creation modes are supported:</strong></p>
 * <ol>
 *   <li><b>From explicit template</b> — caller provides {@link #templateId}. The service
 *       loads the catalog row and copies code, label, category, unit, bounds, antenna,
 *       frequency and modulation. The caller only needs {@code measuredValue}.</li>
 *   <li><b>From measure code</b> — caller provides {@link #measureCode} only. The service
 *       resolves it against {@code (validation.zone.posteType, measureCode)} in
 *       {@code PosteMeasureCatalog}; if a row is found, the rest is copied from there.</li>
 *   <li><b>Ad-hoc</b> — caller provides every field. Used when no catalog entry exists.</li>
 * </ol>
 *
 * <p>That's why most fields below are <b>not</b> annotated as required at the DTO layer.
 * The mandatory-fields check happens in {@code ValidationMeasureServiceImpl.create()} after
 * the catalog lookup, where we know which fields still need a value.</p>
 *
 * <p>The only DTO-layer guarantees are:</p>
 * <ul>
 *   <li>at least one of {@code templateId} or {@code measureCode} must be present;</li>
 *   <li>if {@code measureCode} is given, it must match the canonical pattern;</li>
 *   <li>if both bounds are given, {@code lowerBound < upperBound};</li>
 *   <li>field length constraints still apply.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // tolerate stray fields older callers may still send
public class CreateMeasureRequest {

    /**
     * Catalog template id. {@code catalogTemplateId} is accepted as an alias for
     * back-compat with older frontend code that used the response-side field name on the
     * request payload.
     */
    @JsonAlias({ "catalogTemplateId" })
    private Long templateId;

    @Size(max = 64)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "measureCode must contain only uppercase letters, digits and underscores")
    private String measureCode;

    @Size(max = 255)
    private String measureLabel;

    private MeasureCategory category;

    @Size(max = 16)
    private String unit;

    private Double lowerBound;

    private Double upperBound;

    private Double measuredValue;

    /**
     * Optional zone ID to disambiguate which poste of the line receives this measure.
     * When set, the service uses this to resolve the target {@code ValidationPosteStatus}
     * instead of falling back to "first poste whose posteType matches the template".
     * Required when a line contains multiple postes of the same type (e.g. two WIFI_CONDUIT).
     */
    private Long zoneId;

    @Size(max = 16)
    private String antenna;

    @Min(0)
    private Integer frequencyMhz;

    @Size(max = 32)
    private String modulationScheme;

    /** At least one entry-point identifier is required. */
    @AssertTrue(message = "Either templateId or measureCode must be provided")
    public boolean isIdentifierProvided() {
        return templateId != null || (measureCode != null && !measureCode.isBlank());
    }

    /**
     * If both bounds are provided at this layer (ad-hoc mode), they must be ordered.
     * When bounds come from a catalog template, this is enforced at seed time and the
     * DTO simply leaves both null.
     */
    @AssertTrue(message = "lowerBound must be strictly less than upperBound")
    public boolean isBoundsValid() {
        if (lowerBound == null || upperBound == null) return true;
        return lowerBound < upperBound;
    }
}
