package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.UnmatchedReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmatchedMeasureDTO {
    private String measureCode;
    private Double measuredValue;
    private String unit;
    private UnmatchedReason reason;
    private String reasonDetail;
}
