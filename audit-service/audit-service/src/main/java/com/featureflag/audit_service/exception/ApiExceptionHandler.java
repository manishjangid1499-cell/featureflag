package com.featureflag.audit_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/** Maps transport and security failures without exposing request values or internal causes. */
public abstract class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    protected final ApiProblemDetails problems;

    protected ApiExceptionHandler(ApiProblemDetails problems) {
        this.problems = problems;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest webRequest
    ) {
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            logUnexpectedFailure(exception);
        }
        String detail = status.is5xxServerError()
                ? "An unexpected error occurred" : "The request could not be processed";
        if (status == HttpStatus.NOT_FOUND) {
            detail = "The requested resource was not found";
        }
        ProblemDetail problem = problems.create(status,
                status.is5xxServerError() ? "internal-error" : "invalid-request",
                status.getReasonPhrase(), detail, request);
        Map<String, String> errors = new TreeMap<>();
        if (exception instanceof MethodArgumentNotValidException validation) {
            validation.getBindingResult().getAllErrors().forEach(error ->
                    addError(errors, error instanceof FieldError field
                            ? field.getField() : "request", error.getDefaultMessage()));
        } else if (exception instanceof HandlerMethodValidationException validation
                && !validation.isForReturnValue()) {
            validation.getParameterValidationResults().forEach(result ->
                    result.getResolvableErrors().forEach(error ->
                            addError(errors, result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())));
        }
        if (!errors.isEmpty() && status == HttpStatus.BAD_REQUEST) {
            problem.setProperty("errors", errors);
            problem.setProperty("code", "validation-failed");
            problem.setType(java.net.URI.create("urn:feature-flag-platform:problem:validation-failed"));
            problem.setTitle("Validation Failed");
            problem.setDetail("Validation failed for one or more fields");
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request
    ) {
        Map<String, String> errors = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation ->
                addError(errors, violation.getPropertyPath().toString(), violation.getMessage()));
        ProblemDetail problem = problems.create(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation Failed", "Validation failed for one or more fields", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to access this resource", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unauthorized",
                "Authentication is required", request);
    }

    protected void logUnexpectedFailure(Exception exception) {
        StackTraceElement[] stack = exception.getStackTrace();
        // Exception messages/SQL parameters can contain credentials or request values.
        logger.error("Unexpected internal failure; type=" + exception.getClass().getName()
                + " location=" + (stack.length == 0 ? "unknown" : stack[0]));
    }

    private static void addError(Map<String, String> errors, String field, String message) {
        if (errors.size() < 32) {
            String safeField = field == null ? "request" : field;
            String safeMessage = message == null ? "Invalid value" : message;
            errors.putIfAbsent(safeField.substring(0, Math.min(64, safeField.length())),
                    safeMessage.substring(0, Math.min(160, safeMessage.length())));
        }
    }
}
