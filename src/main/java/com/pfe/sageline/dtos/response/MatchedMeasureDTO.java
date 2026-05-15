package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MeasureStatus;
import com.pfe.sageline.enums.SourceDeclaredStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchedMeasureDTO {
    private String measureCode;
    private String sourceCode;
    private Double measuredValue;
    private String unit;
    private Double lowerBound;
    private Double upperBound;
    private MeasureStatus computedStatus;
    private SourceDeclaredStatus sourceDeclaredStatus;
    private Long templateId;
    private Boolean willPersist;
}
