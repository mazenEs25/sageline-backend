package com.pfe.sageline.dtos.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepValidationRequestDTO {

    private Boolean toolsAvailable;

    private String prepComments; // Comments from TECH_PREP about tooling
}