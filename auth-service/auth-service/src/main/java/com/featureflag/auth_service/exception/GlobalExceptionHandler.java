package com.featureflag.auth_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.featureflag.auth_service.security.LoginRateLimitExceededException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice
@Import(ApiProblemDetails.class)
public class GlobalExceptionHandler extends ApiExceptionHandler {

    public GlobalExceptionHandler(ApiProblemDetails problems) {
        super(problems);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.NOT_FOUND, "resource-not-found", "Not Found",
                "The requested resource was not found", request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbiddenException(
            ForbiddenException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                exception.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(
            BadCredentialsException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Unauthorized",
                "Invalid email or password", request);
    }

    @ExceptionHandler(InvitationConflictException.class)
    public ResponseEntity<ProblemDetail> handleInvitationConflict(
            InvitationConflictException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "invitation-conflict", "Conflict",
                exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOperation(
            InvalidOperationException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.BAD_REQUEST, "invalid-operation", "Invalid Operation",
                exception.getMessage(), request);
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleLoginRateLimit(
            LoginRateLimitExceededException exception, HttpServletRequest request
    ) {
        ProblemDetail problem = problems.create(HttpStatus.TOO_MANY_REQUESTS,
                "login-rate-limited", "Too Many Requests",
                "Too many login attempts. Try again later.", request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "resource-conflict", "Conflict",
                "Request conflicts with an existing resource", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "optimistic-lock-conflict", "Conflict",
                "Resource was modified by another request", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(
            Exception exception, HttpServletRequest request
    ) {
        logUnexpectedFailure(exception);
        return problems.response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request);
    }
}
