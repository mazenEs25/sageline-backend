package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MeasureStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WouldOverwriteMeasureDTO {
    private String measureCode;
    private Double currentValue;
    private MeasureStatus currentStatus;
    private Boolean currentEnteredManually;
    private Double newValue;
    private MeasureStatus newComputedStatus;
}
