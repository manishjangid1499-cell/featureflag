package com.featureflag.auth_service.service;

import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.*;
import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.InvitationStatus;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.repository.InvitationRepository;
import com.featureflag.auth_service.repository.UserRepository;
import com.featureflag.auth_service.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationClient notificationClient;

    @Value("${app.invitation.expiration-hours:48}")
    private int expirationHours;

    @Value("${app.invitation.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /**
     * OWNER can invite: ADMIN, DEVELOPER, VIEWER (cannot invite OWNER)
     * ADMIN can invite: DEVELOPER, VIEWER (cannot invite ADMIN or OWNER)
     */
    @Transactional
    public InvitationResponse inviteMember(InviteMemberRequest request, User currentUser) {
        validateInvitePermission(currentUser.getRole(), request.getRole());

        String email = EmailNormalizer.normalize(request.getEmail());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("User already exists with email: " + email);
        }

        // Invalidate previous pending invitations for this email
        List<Invitation> pendingInvitations = invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING);
        for (Invitation prev : pendingInvitations) {
            prev.setStatus(InvitationStatus.REVOKED);
            invitationRepository.save(prev);
        }

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expirationHours);

        String inviterName = (currentUser.getName() != null && !currentUser.getName().isBlank())
                ? currentUser.getName()
                : currentUser.getEmail();

        Invitation invitation = Invitation.builder()
                .email(email)
                .fullName(request.getName() != null ? request.getName().trim() : "")
                .invitedRole(request.getRole())
                .invitedByUserId(currentUser.getId())
                .invitedByEmail(currentUser.getEmail())
                .invitedByName(inviterName)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Invitation saved = invitationRepository.save(invitation);

        // Send email to the newly invited member
        String acceptUrl = frontendBaseUrl + "/accept-invitation?token=" + rawToken;
        String subject = "You've been invited to FeatureFlag Platform";
        String message = String.format(
                "Hello %s,\n\n" +
                "%s (%s) has invited you to join the FeatureFlag Platform as a %s.\n\n" +
                "This invitation will expire in %d hours.\n\n" +
                "To accept your invitation and set your account password, click the link below:\n" +
                "%s\n\n" +
                "If you did not expect this invitation, you can safely ignore this email.",
                (request.getName() != null && !request.getName().isBlank()) ? request.getName().trim() : "there",
                inviterName,
                currentUser.getEmail(),
                request.getRole().name(),
                expirationHours,
                acceptUrl
        );

        try {
            NotificationDto notificationDto = NotificationDto.builder()
                    .recipient(email)
                    .subject(subject)
                    .message(message)
                    .type("EMAIL")
                    .creatorEmail(currentUser != null ? currentUser.getEmail() : null)
                    .build();

            notificationClient.sendNotification(notificationDto);
            log.info("Invitation email successfully dispatched to {}", email);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}: {}", email, e.getMessage());
            // Rollback invitation so user isn't falsely told invitation succeeded
            throw new RuntimeException("Failed to dispatch invitation email. Please check notification service/mail configuration: " + e.getMessage());
        }

        return toResponse(saved);
    }

    public List<InvitationResponse> getAllInvitations() {
        List<Invitation> list = invitationRepository.findAllByOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();

        // Auto-mark expired
        for (Invitation inv : list) {
            if (inv.getStatus() == InvitationStatus.PENDING && now.isAfter(inv.getExpiresAt())) {
                inv.setStatus(InvitationStatus.EXPIRED);
                invitationRepository.save(inv);
            }
        }

        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public InvitationResponse resendInvitation(Long id, User currentUser) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invitation not found with id: " + id));

        validateInvitePermission(currentUser.getRole(), invitation.getInvitedRole());

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new RuntimeException("This invitation has already been accepted.");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);

        InviteMemberRequest request = new InviteMemberRequest(
                invitation.getFullName(),
                invitation.getEmail(),
                invitation.getInvitedRole()
        );

        return inviteMember(request, currentUser);
    }

    @Transactional
    public String revokeInvitation(Long id, User currentUser) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invitation not found with id: " + id));

        validateInvitePermission(currentUser.getRole(), invitation.getInvitedRole());

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new RuntimeException("Cannot revoke an already accepted invitation.");
        }

        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
        return "Invitation revoked successfully.";
    }

    public ValidateInvitationResponse validateInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return ValidateInvitationResponse.builder()
                    .valid(false)
                    .errorMessage("Invitation token is missing.")
                    .build();
        }

        String tokenHash = hashToken(rawToken.trim());
        Invitation invitation = invitationRepository.findByTokenHash(tokenHash).orElse(null);

        if (invitation == null) {
            return ValidateInvitationResponse.builder()
                    .valid(false)
                    .errorMessage("Invalid or non-existent invitation link.")
                    .build();
        }

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            return ValidateInvitationResponse.builder()
                    .valid(false)
                    .errorMessage("This invitation has already been accepted. Please sign in.")
                    .build();
        }

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            return ValidateInvitationResponse.builder()
                    .valid(false)
                    .errorMessage("This invitation has been revoked by an administrator.")
                    .build();
        }

        if (LocalDateTime.now().isAfter(invitation.getExpiresAt()) || invitation.getStatus() == InvitationStatus.EXPIRED) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            return ValidateInvitationResponse.builder()
                    .valid(false)
                    .errorMessage("This invitation link has expired. Please request a new invitation.")
                    .build();
        }

        return ValidateInvitationResponse.builder()
                .valid(true)
                .email(invitation.getEmail())
                .fullName(invitation.getFullName())
                .role(invitation.getInvitedRole())
                .invitedByName(invitation.getInvitedByName())
                .build();
    }

    @Transactional
    public String acceptInvitation(AcceptInvitationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirm password do not match.");
        }

        if (request.getPassword().length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long.");
        }

        String tokenHash = hashToken(request.getToken().trim());
        Invitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or non-existent invitation token."));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new RuntimeException("This invitation has already been accepted.");
        }

        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new RuntimeException("This invitation has been revoked.");
        }

        if (LocalDateTime.now().isAfter(invitation.getExpiresAt()) || invitation.getStatus() == InvitationStatus.EXPIRED) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new RuntimeException("This invitation has expired. Please request a new invitation.");
        }

        String email = EmailNormalizer.normalize(invitation.getEmail());

        // Create or activate user
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .name(invitation.getFullName())
                    .email(email)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(invitation.getInvitedRole())
                    .build();
        } else {
            user.setName(invitation.getFullName());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(invitation.getInvitedRole());
        }

        userRepository.save(user);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);

        log.info("Invitation accepted and user activated successfully for {}", email);
        return "Account activated successfully. You can now sign in.";
    }

    private void validateInvitePermission(Role currentRole, Role targetRole) {
        if (currentRole == Role.OWNER) {
            if (targetRole == Role.OWNER) {
                throw new ForbiddenException("OWNER cannot invite another OWNER.");
            }
            return;
        }

        if (currentRole == Role.ADMIN) {
            if (targetRole == Role.ADMIN || targetRole == Role.OWNER) {
                throw new ForbiddenException("You do not have permission to invite this role.");
            }
            return;
        }

        throw new ForbiddenException("You do not have permission to invite members.");
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return TOKEN_ENCODER.encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private InvitationResponse toResponse(Invitation inv) {
        return InvitationResponse.builder()
                .id(inv.getId())
                .email(inv.getEmail())
                .fullName(inv.getFullName())
                .invitedRole(inv.getInvitedRole())
                .invitedByUserId(inv.getInvitedByUserId())
                .invitedByEmail(inv.getInvitedByEmail())
                .invitedByName(inv.getInvitedByName())
                .status(inv.getStatus())
                .expiresAt(inv.getExpiresAt())
                .createdAt(inv.getCreatedAt())
                .acceptedAt(inv.getAcceptedAt())
                .build();
    }
}
