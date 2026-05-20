package com.pfe.sageline.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mirrors the frontend {@code BatchValidationMeasureResponse} model.
 *
 * <p>Each item reports per-row success/failure so the UI can highlight the failing rows
 * without rejecting the whole batch.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationMeasureResponse {

    private List<Item> results;
    private Summary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private int index;
        /** "ok" or "error" — matches the frontend string union. */
        private String status;
        private ValidationMeasureResponse measure;
        private ErrorInfo error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int succeeded;
        private int failed;
    }
}
