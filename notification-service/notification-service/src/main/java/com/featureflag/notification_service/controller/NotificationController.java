package com.featureflag.notification_service.controller;

import com.featureflag.notification_service.dto.NotificationRequest;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<Notification> createNotification(
            @Valid @RequestBody NotificationRequest request
    ) {

        Notification notification =
                notificationService.createNotification(
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(notification);
    }

    /**
     * Get notifications for the authenticated user (derived securely from JWT).
     */
    @Operation(summary = "Get authenticated user's notifications")
    @GetMapping
    public ResponseEntity<List<Notification>> getUserNotifications(Authentication authentication) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        String currentUserRole = extractRole(authentication);
        return ResponseEntity.ok(
                notificationService.getNotificationsForUser(currentUserEmail, currentUserRole)
        );
    }

    /**
     * Get notifications for /me endpoint (derived securely from JWT).
     */
    @Operation(summary = "Get notifications for /me")
    @GetMapping("/me")
    public ResponseEntity<List<Notification>> getMyNotifications(Authentication authentication) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        String currentUserRole = extractRole(authentication);
        return ResponseEntity.ok(
                notificationService.getNotificationsForUser(currentUserEmail, currentUserRole)
        );
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
    public ResponseEntity<Notification> getNotificationById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                notificationService.getNotificationById(id)
        );
    }

    /**
     * Get notifications by recipient (secured so users only receive their own notifications).
     */
    @Operation(summary = "Get notifications by recipient")
    @GetMapping("/recipient/{recipient}")
    public ResponseEntity<List<Notification>> getNotificationsByRecipient(
            @PathVariable String recipient,
            Authentication authentication
    ) {
        String currentUserEmail = authentication != null ? authentication.getName() : null;
        if (currentUserEmail != null && !currentUserEmail.equalsIgnoreCase(recipient.trim())) {
            // Prevent unauthorized parameter tampering
            return ResponseEntity.ok(notificationService.getUserNotifications(currentUserEmail));
        }

        return ResponseEntity.ok(
                notificationService.getNotificationsByRecipient(recipient)
        );
    }

    /**
     * Get notifications by status.
     */
    @Operation(summary = "Get notifications by status")
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>> getNotificationsByStatus(
            @PathVariable String status
    ) {

        return ResponseEntity.ok(
                notificationService.getNotificationsByStatus(status)
        );
    }

    /**
     * Delete notification.
     */
    @Operation(summary = "Delete notification")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNotification(
            @PathVariable Long id
    ) {

        notificationService.deleteNotification(id);

        return ResponseEntity.ok(
                "Notification deleted successfully"
        );
    }
}