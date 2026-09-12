package com.featureflag.notification_service.exception;

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
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.NOT_FOUND, "resource-not-found", "Not Found",
                "The requested resource was not found", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.BAD_REQUEST, "invalid-request", "Bad Request",
                "Invalid request", request);
    }

    @ExceptionHandler(InvitationDeliveryException.class)
    public ResponseEntity<ProblemDetail> handleInvitationDelivery(
            InvitationDeliveryException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.BAD_GATEWAY, "notification-delivery-failed", "Bad Gateway",
                "Invitation email delivery failed", request);
    }

    @ExceptionHandler(NotificationConflictException.class)
    public ResponseEntity<ProblemDetail> handleNotificationConflict(
            NotificationConflictException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.CONFLICT, "notification-conflict", "Conflict",
                exception.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(
            ForbiddenException exception, HttpServletRequest request
    ) {
        return problems.response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception exception, HttpServletRequest request
    ) {
        logUnexpectedFailure(exception);
        return problems.response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request);
    }
}
