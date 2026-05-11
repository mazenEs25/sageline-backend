package com.pfe.sageline.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchErrorResponse {

    private int status;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<BatchValidationException.BatchValidationError> errors;
}
