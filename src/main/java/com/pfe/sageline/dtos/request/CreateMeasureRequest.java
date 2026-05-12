package com.pfe.sageline.dtos.request;

import com.pfe.sageline.enums.MeasureCategory;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMeasureRequest {

    private Long templateId;

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Z0-9_]+$")
    private String measureCode;

    @NotBlank
    @Size(max = 255)
    private String measureLabel;

    @NotNull
    private MeasureCategory category;

    @NotBlank
    @Size(max = 16)
    private String unit;

    @NotNull
    private Double lowerBound;

    @NotNull
    private Double upperBound;

    private Double measuredValue;

    @Size(max = 16)
    private String antenna;

    @Min(0)
    private Integer frequencyMhz;

    @Size(max = 32)
    private String modulationScheme;

    @AssertTrue(message = "lowerBound must be < upperBound")
    public boolean isBoundsValid() {
        return lowerBound != null && upperBound != null && lowerBound < upperBound;
    }
}
