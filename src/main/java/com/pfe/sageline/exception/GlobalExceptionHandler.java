package com.pfe.sageline.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Gestion des erreurs de ressource non trouvée (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {

        log.error("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Gestion des erreurs de validation (400)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            WebRequest request) {

        log.error("Validation error: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs de validation des DTOs (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        log.error("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erreur de validation des données",
                LocalDateTime.now(),
                errors
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs IllegalArgumentException (400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        log.error("Illegal argument: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestion des erreurs de template catalogue en doublon (409)
     */
    @ExceptionHandler(DuplicateCatalogTemplateException.class)
    public ResponseEntity<ConflictErrorResponse> handleDuplicateCatalogTemplateException(
            DuplicateCatalogTemplateException ex,
            WebRequest request) {

        log.error("Duplicate catalog template: {}", ex.getMessage());

        ConflictErrorResponse errorResponse = new ConflictErrorResponse(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false),
                ex.getConflictingCodes()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Gestion des erreurs de violation des bornes (422)
     */
    @ExceptionHandler(BoundsViolationException.class)
    public ResponseEntity<ErrorResponse> handleBoundsViolationException(
            BoundsViolationException ex,
            WebRequest request) {

        log.error("Bounds violation: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Gestion des erreurs de validation batch (422)
     */
    @ExceptionHandler(BatchValidationException.class)
    public ResponseEntity<BatchErrorResponse> handleBatchValidationException(
            BatchValidationException ex,
            WebRequest request) {

        log.error("Batch validation error: {}", ex.getMessage());

        BatchErrorResponse errorResponse = new BatchErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false),
                ex.getErrors()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.pfe.sageline.exception.TransitionBlockedException.class)
    public org.springframework.http.ResponseEntity<com.pfe.sageline.dtos.response.WorkflowReadinessDTO>
            handleTransitionBlocked(com.pfe.sageline.exception.TransitionBlockedException ex) {
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ex.getReadiness());
    }

    @ExceptionHandler(MeasureNotEditableException.class)
    public ResponseEntity<Map<String, Object>> handleMeasureNotEditable(MeasureNotEditableException ex) {
        log.error("Measure not editable: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 422);
        body.put("error", "TICKET_NOT_EDITABLE");
        body.put("message", ex.getMessage());
        body.put("ticketId", ex.getTicketId());
        body.put("currentStatus", ex.getCurrentStatus());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(BatchMeasureValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBatchMeasureValidation(BatchMeasureValidationException ex) {
        log.error("Batch measure validation error: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "BATCH_REJECTED");
        body.put("totalEntries", ex.getTotalEntries());
        body.put("failedEntries", ex.getFailedEntries());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        String cause = ex.getCause() != null && ex.getCause().getMessage() != null
                ? ex.getCause().getMessage() : "";
        boolean isNaturalKeyViolation = (msg.contains("uq_vm_natural_key") || cause.contains("uq_vm_natural_key"))
                && (cause.contains("23505") || msg.contains("23505")
                    || (ex.getCause() != null && ex.getCause().getClass().getName().contains("PSQLException")
                        && cause.contains("uq_vm_natural_key")));

        if (isNaturalKeyViolation || (cause.contains("uq_vm_natural_key"))) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 409);
            body.put("error", "DUPLICATE_MEASURE");
            body.put("message", "Duplicate measure on this ticket for the given (code, antenna, frequency, modulation)");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        throw ex;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                LocalDateTime.now(),
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Bad Request");
        body.put("message", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(UnsupportedLogFormatException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedLogFormat(UnsupportedLogFormatException ex) {
        log.error("Unsupported log format: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 422);
        body.put("code", "UNSUPPORTED_LOG_FORMAT");
        body.put("message", ex.getMessage());
        body.put("details", Map.of("headerSample", ex.getHeaderSample()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(LogParseException.class)
    public ResponseEntity<Map<String, Object>> handleLogParseException(LogParseException ex) {
        log.error("Log parse error: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 422);
        body.put("code", "LOG_PARSE_ERROR");
        body.put("message", ex.getMessage());
        body.put("details", Map.of("parserNotes", ex.getParserNotes()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(LogTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleLogTooLarge(LogTooLargeException ex) {
        log.error("Log too large: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 413);
        body.put("code", "LOG_TOO_LARGE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(ImportInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleImportInProgress(ImportInProgressException ex) {
        log.error("Import in progress: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 409);
        body.put("code", "IMPORT_IN_PROGRESS");
        body.put("message", ex.getMessage());
        body.put("validationId", ex.getValidationId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.error("Upload size exceeded: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 413);
        body.put("code", "LOG_TOO_LARGE");
        body.put("message", "Upload exceeds 2MB limit");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    /**
     * Gestion des erreurs génériques (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {

        log.error("Internal server error: ", ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Une erreur interne est survenue",
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Gestion des erreurs RuntimeException (500)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            WebRequest request) {

        log.error("Runtime exception: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}