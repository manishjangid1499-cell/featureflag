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
    @Operation(
            summary = "Create and send notification"
    )
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
     * Get all notifications.
     */
    @Operation(
            summary = "Get all notifications"
    )
    @GetMapping
    public ResponseEntity<List<Notification>>
    getAllNotifications() {

        return ResponseEntity.ok(
                notificationService.getAllNotifications()
        );
    }

    /**
     * Get notification by ID.
     */
    @Operation(
            summary = "Get notification by ID"
    )
    @GetMapping("/{id}")
    public ResponseEntity<Notification>
    getNotificationById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                notificationService
                        .getNotificationById(id)
        );
    }

    /**
     * Get notifications by recipient.
     */
    @Operation(
            summary = "Get notifications by recipient"
    )
    @GetMapping("/recipient/{recipient}")
    public ResponseEntity<List<Notification>>
    getNotificationsByRecipient(
            @PathVariable String recipient
    ) {

        return ResponseEntity.ok(
                notificationService
                        .getNotificationsByRecipient(
                                recipient
                        )
        );
    }

    /**
     * Get notifications by status.
     */
    @Operation(
            summary = "Get notifications by status"
    )
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>>
    getNotificationsByStatus(
            @PathVariable String status
    ) {

        return ResponseEntity.ok(
                notificationService
                        .getNotificationsByStatus(
                                status
                        )
        );
    }

    /**
     * Delete notification.
     */
    @Operation(
            summary = "Delete notification"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<String>
    deleteNotification(
            @PathVariable Long id
    ) {

        notificationService.deleteNotification(id);

        return ResponseEntity.ok(
                "Notification deleted successfully"
        );
    }
}