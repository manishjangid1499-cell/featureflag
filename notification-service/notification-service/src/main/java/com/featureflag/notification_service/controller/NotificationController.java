package com.featureflag.notification_service.controller;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.dto.NotificationResponse;
import com.featureflag.notification_service.dto.PageResponse;
import com.featureflag.notification_service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Create and send notification.
     */
    @Operation(summary = "Create and send notification")
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody NotificationRequest request,
            Authentication authentication
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(NotificationResponse.from(
                        notificationService.createNotification(
                                request,
                                authentication.getName()
                        )
                ));
    }

    /**
     * Get notifications for the authenticated user (derived securely from JWT).
     */
    @Operation(summary = "Get authenticated user's notifications")
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getUserNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        String currentUserRole = extractRole(authentication);
        return ResponseEntity.ok(PageResponse.from(
                notificationService.getNotificationsForUser(
                        currentUserEmail,
                        currentUserRole,
                        PageRequest.of(page, size)
                ),
                NotificationResponse::from
        ));
    }

    /**
     * Get notifications for /me endpoint (derived securely from JWT).
     */
    @Operation(summary = "Get notifications for /me")
    @GetMapping("/me")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        String currentUserRole = extractRole(authentication);
        return ResponseEntity.ok(PageResponse.from(
                notificationService.getNotificationsForUser(
                        currentUserEmail,
                        currentUserRole,
                        PageRequest.of(page, size)
                ),
                NotificationResponse::from
        ));
    }

    private String extractRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return "VIEWER";
        }
        String authName = authentication.getAuthorities().iterator().next().getAuthority();
        return authName.startsWith("ROLE_") ? authName.substring(5) : authName;
    }

    /**
     * Get notification by ID.
     */
    @Operation(summary = "Get notification by ID")
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        return ResponseEntity.ok(NotificationResponse.from(
                notificationService.getNotificationById(
                        id,
                        authentication.getName(),
                        extractRole(authentication)
                )
        ));
    }

    /**
     * Get notifications by recipient (secured so users only receive their own notifications).
     */
    @Operation(summary = "Get notifications by recipient")
    @GetMapping("/recipient/{recipient}")
    public ResponseEntity<PageResponse<NotificationResponse>> getNotificationsByRecipient(
            @PathVariable String recipient,
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                notificationService.getNotificationsByRecipient(
                        recipient,
                        authentication.getName(),
                        extractRole(authentication),
                        PageRequest.of(page, size)
                ),
                NotificationResponse::from
        ));
    }

    /**
     * Get notifications by status.
     */
    @Operation(summary = "Get notifications by status")
    @GetMapping("/status/{status}")
    public ResponseEntity<PageResponse<NotificationResponse>> getNotificationsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(PageResponse.from(
                notificationService.getNotificationsByStatus(
                        status,
                        PageRequest.of(page, size)
                ),
                NotificationResponse::from
        ));
    }

    /**
     * Delete notification.
     */
    @Operation(summary = "Delete notification")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNotification(
            @PathVariable Long id,
            Authentication authentication
    ) {

        notificationService.deleteNotification(
                id,
                authentication.getName(),
                extractRole(authentication)
        );

        return ResponseEntity.ok(
                "Notification deleted successfully"
        );
    }
}
