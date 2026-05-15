package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.WarningCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningDTO {
    private WarningCode code;
    private String measureCode;
    private String message;
}
