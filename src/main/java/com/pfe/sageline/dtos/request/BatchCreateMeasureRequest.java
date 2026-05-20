package com.pfe.sageline.dtos.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Batch payload for {@code POST /api/validations/{id}/measures/batch}.
 *
 * <p>The canonical field is {@code measures}, but the frontend
 * {@code BatchCreateValidationMeasureRequest} model used {@code items} from day one.
 * Rather than break either side, we accept both via {@link JsonAlias}.</p>
 */
@Data
public class BatchCreateMeasureRequest {

    @NotEmpty
    @Size(max = 200)
    @Valid
    @JsonAlias({ "items" })
    private List<CreateMeasureRequest> measures;
}
