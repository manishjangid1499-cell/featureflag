package com.featureflag.notification_service.controller;

import com.featureflag.notification_service.dto.InvitationEmailRequest;
import com.featureflag.notification_service.service.InvitationEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final InvitationEmailService invitationEmailService;

    @PostMapping("/invitations")
    public ResponseEntity<Void> sendInvitationEmail(
            @Valid @RequestBody InvitationEmailRequest request
    ) {
        invitationEmailService.sendInvitationEmail(request);
        return ResponseEntity.accepted().build();
    }
}
