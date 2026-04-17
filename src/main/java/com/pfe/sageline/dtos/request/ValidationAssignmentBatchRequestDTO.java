package com.pfe.sageline.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationAssignmentBatchRequestDTO {

    @NotEmpty(message = "Au moins une affectation est requise")
    @Valid
    private List<ValidationAssignmentRequestDTO> assignments;
}