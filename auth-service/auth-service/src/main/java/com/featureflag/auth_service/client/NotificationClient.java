package com.featureflag.auth_service.client;

import com.featureflag.auth_service.dto.InvitationNotificationDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "NOTIFICATION-SERVICE")
public interface NotificationClient {

    @PostMapping("/internal/notifications/invitations")
    void sendInvitationEmail(
            @RequestHeader("X-Notification-Service-Key") String serviceKey,
            @RequestBody InvitationNotificationDto request
    );
}
