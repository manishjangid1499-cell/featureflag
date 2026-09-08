package com.featureflag.audit_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request
    ) {
        logUnexpectedFailure(exception);
        return problems.response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request);
    }
}
