package com.pfe.sageline.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload for {@code PUT /api/validations/{id}/measures/batch}.
 * Used by the MeasurePanel "Édition groupée" flow on the frontend.
 *
 * <p>Each item carries only the measure id + the new {@code measuredValue}
 * (null is allowed and means "reset to NOT_EXECUTED"). All other fields stay as-is.</p>
 */
@Data
@NoArgsConstructor
public class BatchUpdateMeasureRequest {

    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<Item> items;

    @Data
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Long id;
        /** Null is allowed — resets the measure to NOT_EXECUTED. */
        private Double measuredValue;
    }
}
