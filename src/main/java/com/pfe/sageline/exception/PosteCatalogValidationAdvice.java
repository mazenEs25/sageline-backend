package com.pfe.sageline.exception;

import com.pfe.sageline.controller.PosteCatalogController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Catalog-scoped advice: bean-validation failures (@Valid) on the catalog endpoints
 * return HTTP 422 to match the published OpenAPI contract (FR-018, contracts/poste-catalog-api.openapi.yaml).
 *
 * <p>The project-wide {@link GlobalExceptionHandler} maps {@code MethodArgumentNotValidException}
 * to HTTP 400 for every other controller; that legacy behavior is preserved by limiting this
 * advice via {@code assignableTypes}. {@code @Order(HIGHEST_PRECEDENCE)} ensures Spring picks
 * this advice first for the targeted controller.
 */
@RestControllerAdvice(assignableTypes = PosteCatalogController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class PosteCatalogValidationAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = (err instanceof FieldError fe) ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        log.warn("Catalog validation failure: {}", errors);
        ValidationErrorResponse body = new ValidationErrorResponse(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "Validation failed",
            LocalDateTime.now(),
            errors
        );
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
