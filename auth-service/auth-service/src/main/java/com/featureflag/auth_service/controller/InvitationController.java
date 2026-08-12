package com.featureflag.auth_service.controller;

import com.featureflag.auth_service.dto.AcceptInvitationRequest;
import com.featureflag.auth_service.dto.ValidateInvitationResponse;
import com.featureflag.auth_service.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitation Acceptance APIs", description = "Public endpoints for validating and accepting member invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "Validate invitation token")
    @GetMapping("/validate")
    public ResponseEntity<ValidateInvitationResponse> validateInvitation(@RequestParam String token) {
        ValidateInvitationResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Accept invitation and create account password")
    @PostMapping("/accept")
    public ResponseEntity<String> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        String message = invitationService.acceptInvitation(request);
        return ResponseEntity.ok(message);
    }
}
