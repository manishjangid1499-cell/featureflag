package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.*;
import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.InvitationStatus;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.exception.InvitationConflictException;
import com.featureflag.auth_service.repository.InvitationRepository;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-02T12:00:00Z");

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InvitationNotificationDispatcher invitationNotificationDispatcher;

    private InvitationService invitationService;

    private User ownerUser;
    private User adminUser;
    private User devUser;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                invitationRepository,
                userRepository,
                passwordEncoder,
                invitationNotificationDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(
                invitationService,
                "expirationHours",
                48
        );
        ReflectionTestUtils.setField(
                invitationService,
                "frontendBaseUrl",
                "http://localhost:5173"
        );

        ownerUser = User.builder()
                .id(1L)
                .email("owner@company.com")
                .name("Owner")
                .role(Role.OWNER)
                .build();

        adminUser = User.builder()
                .id(2L)
                .email("admin@company.com")
                .name("Admin")
                .role(Role.ADMIN)
                .build();

        devUser = User.builder()
                .id(3L)
                .email("dev@company.com")
                .name("Dev")
                .role(Role.DEVELOPER)
                .build();
    }

    @Test
    @DisplayName("Invite Member - OWNER can invite ADMIN")
    void testInviteMember_OwnerCanInviteAdmin() {
        InviteMemberRequest request =
                new InviteMemberRequest(
                        "New Admin",
                        "newadmin@company.com",
                        Role.ADMIN
                );

        when(userRepository.findByEmail("newadmin@company.com"))
                .thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatusForUpdate(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> {
                    Invitation invitation = invocation.getArgument(0);
                    invitation.setId(100L);
                    return invitation;
                });

        InvitationResponse response =
                invitationService.inviteMember(request, ownerUser);

        assertNotNull(response);
        assertEquals("newadmin@company.com", response.getEmail());
        assertEquals(Role.ADMIN, response.getInvitedRole());
        assertEquals(InvitationStatus.PENDING, response.getStatus());

        verify(invitationNotificationDispatcher)
                .dispatchAfterCommit(any(InvitationNotificationDto.class), any());
        verify(invitationRepository)
                .findByEmailAndStatusForUpdate(
                        "newadmin@company.com",
                        InvitationStatus.PENDING
                );
        InOrder lockingOrder = inOrder(invitationRepository, userRepository);
        lockingOrder.verify(invitationRepository)
                .findByEmailAndStatusForUpdate(
                        "newadmin@company.com",
                        InvitationStatus.PENDING
                );
        lockingOrder.verify(userRepository)
                .findByEmail("newadmin@company.com");
    }

    @Test
    @DisplayName("Invite Member - sends only structured invitation data after save")
    void testInviteMember_UsesConfiguredFrontendOrigin() {
        ReflectionTestUtils.setField(
                invitationService,
                "frontendBaseUrl",
                "https://frontend.example.test"
        );

        InviteMemberRequest request =
                new InviteMemberRequest(
                        "New Admin",
                        "newadmin@company.com",
                        Role.ADMIN
                );

        when(userRepository.findByEmail("newadmin@company.com"))
                .thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatusForUpdate(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> {
                    Invitation invitation = invocation.getArgument(0);
                    invitation.setId(102L);
                    return invitation;
                });

        invitationService.inviteMember(request, ownerUser);

        ArgumentCaptor<InvitationNotificationDto> captor =
                ArgumentCaptor.forClass(InvitationNotificationDto.class);

        verify(invitationNotificationDispatcher)
                .dispatchAfterCommit(captor.capture(), any());

        InvitationNotificationDto notification = captor.getValue();

        assertEquals("newadmin@company.com", notification.getRecipient());
        assertEquals("New Admin", notification.getInviteeName());
        assertEquals("Owner", notification.getInviterName());
        assertEquals("owner@company.com", notification.getInviterEmail());
        assertEquals("ADMIN", notification.getRole());
        assertEquals(48, notification.getExpirationHours());
        assertTrue(
                notification.getAcceptanceUrl().startsWith(
                        "https://frontend.example.test/accept-invitation?token="
                )
        );
        assertFalse(notification.getAcceptanceUrl().contains("localhost:5173"));
    }

    @Test
    @DisplayName("Invite Member - OWNER cannot invite another OWNER")
    void testInviteMember_OwnerCannotInviteOwner() {
        InviteMemberRequest request =
                new InviteMemberRequest(
                        "Second Owner",
                        "owner2@company.com",
                        Role.OWNER
                );

        assertThrows(
                ForbiddenException.class,
                () -> invitationService.inviteMember(request, ownerUser)
        );

        verify(invitationRepository, never())
                .save(any(Invitation.class));
        verifyNoInteractions(invitationNotificationDispatcher);
    }

    @Test
    @DisplayName("Invite Member - ADMIN can invite DEVELOPER")
    void testInviteMember_AdminCanInviteDeveloper() {
        InviteMemberRequest request =
                new InviteMemberRequest(
                        "New Dev",
                        "newdev@company.com",
                        Role.DEVELOPER
                );

        when(userRepository.findByEmail("newdev@company.com"))
                .thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatusForUpdate(anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> {
                    Invitation invitation = invocation.getArgument(0);
                    invitation.setId(101L);
                    return invitation;
                });

        InvitationResponse response =
                invitationService.inviteMember(request, adminUser);

        assertNotNull(response);
        assertEquals(Role.DEVELOPER, response.getInvitedRole());

        verify(invitationNotificationDispatcher)
                .dispatchAfterCommit(any(InvitationNotificationDto.class), any());
    }

    @Test
    @DisplayName("Invite Member - ADMIN cannot invite ADMIN")
    void testInviteMember_AdminCannotInviteAdmin() {
        InviteMemberRequest request =
                new InviteMemberRequest(
                        "Another Admin",
                        "admin2@company.com",
                        Role.ADMIN
                );

        assertThrows(
                ForbiddenException.class,
                () -> invitationService.inviteMember(request, adminUser)
        );

        verify(invitationRepository, never())
                .save(any(Invitation.class));
        verifyNoInteractions(invitationNotificationDispatcher);
    }

    @Test
    @DisplayName("Invite Member - DEVELOPER cannot invite any member")
    void testInviteMember_DeveloperCannotInvite() {
        InviteMemberRequest request =
                new InviteMemberRequest(
                        "Any Member",
                        "member@company.com",
                        Role.VIEWER
                );

        assertThrows(
                ForbiddenException.class,
                () -> invitationService.inviteMember(request, devUser)
        );

        verify(invitationRepository, never())
                .save(any(Invitation.class));
        verifyNoInteractions(invitationNotificationDispatcher);
    }

    @Test
    @DisplayName("Invite Member - Locks and revokes existing pending invitations")
    void testInviteMember_LocksAndRevokesExistingPendingInvitations() {
        Invitation previousInvitation = Invitation.builder()
                .id(50L)
                .email("newdev@company.com")
                .fullName("New Dev")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(NOW.plusSeconds(24 * 60 * 60))
                .tokenHash("previous-hash")
                .build();
        InviteMemberRequest request = new InviteMemberRequest(
                "New Dev",
                " NewDev@Company.COM ",
                Role.DEVELOPER
        );

        when(userRepository.findByEmail("newdev@company.com"))
                .thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatusForUpdate(
                "newdev@company.com",
                InvitationStatus.PENDING
        )).thenReturn(List.of(previousInvitation));
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        invitationService.inviteMember(request, ownerUser);

        assertEquals(InvitationStatus.REVOKED, previousInvitation.getStatus());
        verify(invitationRepository).save(previousInvitation);
    }

    @Test
    @DisplayName("Validate Invitation - Expired Token Returns Invalid Status")
    void testValidateInvitation_ExpiredToken() {
        Invitation expiredInvitation = Invitation.builder()
                .id(1L)
                .email("expired@company.com")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(NOW.minusSeconds(60 * 60))
                .tokenHash("somehash")
                .build();

        when(invitationRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(expiredInvitation));

        ValidateInvitationResponse response =
                invitationService.validateInvitation("valid_raw_token");

        assertNotNull(response);
        assertFalse(response.isValid());
        assertTrue(response.getErrorMessage().contains("expired"));
    }

    @Test
    @DisplayName("Accept Invitation - Success creates user with hashed password and marks ACCEPTED")
    void testAcceptInvitation_Success() {
        Invitation invitation = Invitation.builder()
                .id(1L)
                .email(" Invitee@Company.COM ")
                .fullName("Invitee")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(NOW.plusSeconds(24 * 60 * 60))
                .tokenHash("hash")
                .build();

        AcceptInvitationRequest request =
                new AcceptInvitationRequest(
                        "raw_token",
                        "securePassword123",
                        "securePassword123"
                );

        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail("invitee@company.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123"))
                .thenReturn("hashed_pass");

        String result = invitationService.acceptInvitation(request);

        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        assertNotNull(invitation.getAcceptedAt());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("invitee@company.com", savedUser.getEmail());
        assertEquals("Invitee", savedUser.getName());
        assertEquals("hashed_pass", savedUser.getPassword());
        assertEquals(Role.DEVELOPER, savedUser.getRole());

        verify(passwordEncoder).encode("securePassword123");
        verify(invitationRepository).findByTokenHashForUpdate(anyString());
        verify(invitationRepository, never()).findByTokenHash(anyString());
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("Accept Invitation - Existing account is rejected without mutation")
    void testAcceptInvitation_ExistingAccountRejectedWithoutMutation() {
        Invitation invitation = pendingInvitation();
        User existingUser = User.builder()
                .id(99L)
                .name("Existing Name")
                .email("invitee@company.com")
                .password("existing-password-hash")
                .role(Role.ADMIN)
                .build();
        AcceptInvitationRequest request = validAcceptRequest();

        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail("invitee@company.com"))
                .thenReturn(Optional.of(existingUser));

        InvitationConflictException exception = assertThrows(
                InvitationConflictException.class,
                () -> invitationService.acceptInvitation(request)
        );

        assertEquals("Invitation can no longer be accepted.", exception.getMessage());
        assertEquals("Existing Name", existingUser.getName());
        assertEquals("existing-password-hash", existingUser.getPassword());
        assertEquals(Role.ADMIN, existingUser.getRole());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertNull(invitation.getAcceptedAt());

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    @DisplayName("Accept Invitation - Already accepted invitation returns conflict")
    void testAcceptInvitation_AlreadyAcceptedReturnsConflict() {
        Instant acceptedAt = NOW.minusSeconds(5 * 60);
        Invitation invitation = pendingInvitation();
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(acceptedAt);
        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(invitation));

        assertThrows(
                InvitationConflictException.class,
                () -> invitationService.acceptInvitation(validAcceptRequest())
        );

        assertEquals(acceptedAt, invitation.getAcceptedAt());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    @DisplayName("Accept Invitation - Revoked invitation is rejected without user mutation")
    void testAcceptInvitation_RevokedInvitationRejected() {
        Invitation invitation = pendingInvitation();
        invitation.setStatus(InvitationStatus.REVOKED);
        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(invitation));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> invitationService.acceptInvitation(validAcceptRequest())
        );

        assertTrue(exception.getMessage().contains("revoked"));
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    @DisplayName("Accept Invitation - Expired invitation is rejected without status save")
    void testAcceptInvitation_ExpiredInvitationRejectedWithoutSave() {
        Invitation invitation = pendingInvitation();
        invitation.setExpiresAt(NOW.minusSeconds(60));
        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.of(invitation));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> invitationService.acceptInvitation(validAcceptRequest())
        );

        assertTrue(exception.getMessage().contains("expired"));
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertNull(invitation.getAcceptedAt());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    @DisplayName("Accept Invitation - Invalid token is rejected safely")
    void testAcceptInvitation_InvalidTokenRejected() {
        when(invitationRepository.findByTokenHashForUpdate(anyString()))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> invitationService.acceptInvitation(validAcceptRequest())
        );

        assertEquals(
                "Invalid or non-existent invitation token.",
                exception.getMessage()
        );
        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
        verify(invitationRepository, never()).save(any(Invitation.class));
    }

    @Test
    @DisplayName("Revoke Invitation - Uses locked lookup")
    void testRevokeInvitation_UsesLockedLookup() {
        Invitation invitation = pendingInvitation();
        when(invitationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(invitation));

        String result = invitationService.revokeInvitation(1L, ownerUser);

        assertEquals("Invitation revoked successfully.", result);
        assertEquals(InvitationStatus.REVOKED, invitation.getStatus());
        verify(invitationRepository).findByIdForUpdate(1L);
        verify(invitationRepository, never()).findById(anyLong());
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("Resend Invitation - Uses locked lookup and creates a new secure token")
    void testResendInvitation_UsesLockedLookupAndCreatesNewToken() throws Exception {
        Invitation oldInvitation = pendingInvitation();
        when(invitationRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(oldInvitation));
        when(userRepository.findByEmail("invitee@company.com"))
                .thenReturn(Optional.empty());
        when(invitationRepository.findByEmailAndStatusForUpdate(
                "invitee@company.com",
                InvitationStatus.PENDING
        )).thenReturn(Collections.emptyList());
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> {
                    Invitation saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(2L);
                    }
                    return saved;
                });

        InvitationResponse response = invitationService.resendInvitation(1L, ownerUser);

        assertEquals(InvitationStatus.REVOKED, oldInvitation.getStatus());
        assertEquals(InvitationStatus.PENDING, response.getStatus());
        assertEquals(2L, response.getId());
        verify(invitationRepository).findByIdForUpdate(1L);
        verify(invitationRepository, never()).findById(anyLong());

        ArgumentCaptor<Invitation> invitationCaptor =
                ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository, times(2)).save(invitationCaptor.capture());
        Invitation newInvitation = invitationCaptor.getAllValues().stream()
                .filter(saved -> !saved.getId().equals(oldInvitation.getId()))
                .findFirst()
                .orElseThrow();

        ArgumentCaptor<InvitationNotificationDto> notificationCaptor =
                ArgumentCaptor.forClass(InvitationNotificationDto.class);
        verify(invitationNotificationDispatcher)
                .dispatchAfterCommit(notificationCaptor.capture(), any());
        String acceptanceUrl = notificationCaptor.getValue().getAcceptanceUrl();
        String rawToken = acceptanceUrl.substring(acceptanceUrl.indexOf("token=") + 6);
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(rawToken.getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(43, rawToken.length());
        assertEquals(expectedHash, newInvitation.getTokenHash());
    }

    @Test
    @DisplayName("Accept Invitation - Password mismatch throws exception")
    void testAcceptInvitation_PasswordMismatch() {
        AcceptInvitationRequest request =
                new AcceptInvitationRequest(
                        "token",
                        "password123",
                        "differentPassword"
                );

        assertThrows(
                RuntimeException.class,
                () -> invitationService.acceptInvitation(request)
        );
    }

    private Invitation pendingInvitation() {
        return Invitation.builder()
                .id(1L)
                .email("invitee@company.com")
                .fullName("Invitee")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(NOW.plusSeconds(24 * 60 * 60))
                .tokenHash("hash")
                .build();
    }

    private AcceptInvitationRequest validAcceptRequest() {
        return new AcceptInvitationRequest(
                "raw_token",
                "securePassword123",
                "securePassword123"
        );
    }
}
