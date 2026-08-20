package com.featureflag.auth_service.service;

import com.featureflag.auth_service.dto.*;
import com.featureflag.auth_service.entity.Invitation;
import com.featureflag.auth_service.entity.InvitationStatus;
import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.exception.ForbiddenException;
import com.featureflag.auth_service.repository.InvitationRepository;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InvitationNotificationDispatcher invitationNotificationDispatcher;

    @InjectMocks
    private InvitationService invitationService;

    private User ownerUser;
    private User adminUser;
    private User devUser;

    @BeforeEach
    void setUp() {
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
        when(invitationRepository.findByEmailAndStatus(anyString(), any()))
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
                .dispatchAfterCommit(any(InvitationNotificationDto.class));
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
        when(invitationRepository.findByEmailAndStatus(anyString(), any()))
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
                .dispatchAfterCommit(captor.capture());

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
        when(invitationRepository.findByEmailAndStatus(anyString(), any()))
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
                .dispatchAfterCommit(any(InvitationNotificationDto.class));
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
    @DisplayName("Validate Invitation - Expired Token Returns Invalid Status")
    void testValidateInvitation_ExpiredToken() {
        Invitation expiredInvitation = Invitation.builder()
                .id(1L)
                .email("expired@company.com")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusHours(1))
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
                .email("invitee@company.com")
                .fullName("Invitee")
                .invitedRole(Role.DEVELOPER)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .tokenHash("hash")
                .build();

        AcceptInvitationRequest request =
                new AcceptInvitationRequest(
                        "raw_token",
                        "securePassword123",
                        "securePassword123"
                );

        when(invitationRepository.findByTokenHash(anyString()))
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

        verify(userRepository).save(any(User.class));
        verify(invitationRepository).save(invitation);
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
}
