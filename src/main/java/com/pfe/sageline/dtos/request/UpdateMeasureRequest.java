package com.pfe.sageline.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMeasureRequest {

    private Double measuredValue;

    private Double lowerBound;

    private Double upperBound;

    @Size(max = 16)
    private String unit;

    @Size(max = 16)
    private String antenna;

    @Min(0)
    private Integer frequencyMhz;

    @Size(max = 32)
    private String modulationScheme;

    @AssertTrue(message = "when both bounds present, lower < upper")
    public boolean isBoundsValid() {
        if (lowerBound == null || upperBound == null) {
            return true;
        }
        return lowerBound < upperBound;
    }
}
