package com.pfe.sageline.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchCreateMeasureRequest {

    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<CreateMeasureRequest> measures;
}
