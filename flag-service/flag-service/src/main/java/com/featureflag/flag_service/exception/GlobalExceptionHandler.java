package com.featureflag.flag_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
@Import(ApiProblemDetails.class)
public class GlobalExceptionHandler extends ApiExceptionHandler {

    public GlobalExceptionHandler(ApiProblemDetails problems) {
        super(problems);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.NOT_FOUND, "resource-not-found", "Not Found",
                "The requested resource was not found", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "feature-flag-conflict", "Conflict",
                "Feature flag conflicts with existing key and environment", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "optimistic-lock-conflict", "Conflict",
                "Feature flag was modified by another request", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(
            IllegalArgumentException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.BAD_REQUEST, "invalid-request", "Bad Request",
                "Invalid request", request);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOperation(
            InvalidOperationException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.BAD_REQUEST, "invalid-operation", "Invalid Operation",
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(
            Exception exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request);
    }
}
