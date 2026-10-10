package com.featureflag.auth_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.*;
import com.featureflag.auth_service.entity.*;
import com.featureflag.auth_service.repository.InvitationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.featureflag.auth_service.exception.ForbiddenException;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "NOTIFICATION_INTERNAL_SERVICE_KEY=invitation-test-key")
@ActiveProfiles("test")
@Import({InvitationService.class, InvitationNotificationDispatcher.class,
        InvitationDeliveryIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationDeliveryIntegrationTest {
    @Autowired private InvitationService invitations;
    @Autowired private InvitationRepository repository;
    @Autowired private InvitationNotificationDispatcher dispatcher;
    @MockitoBean private NotificationClient client;
    @MockitoBean private PasswordEncoder encoder;

    @TestConfiguration
    static class TimeConfig {
        @Bean Clock invitationClock() { return Clock.systemUTC(); }
    }

    @BeforeEach
    void clearInvitations() { repository.deleteAll(); }

    @Test
    void successfulDispatchIsReportedAfterRealTransactionCommit() {
        assertDeliveryResult(true);
    }

    @Test
    void failedDispatchPreservesCommittedInvitationAndReportsFailure() {
        doThrow(new IllegalStateException("Delivery unavailable"))
                .when(client).sendInvitationEmail(anyString(), any());
        assertDeliveryResult(false);
    }

    @Test
    void missingServiceAuthenticationReportsFailureWithoutCallingNotification() {
        org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "notificationInternalServiceKey", "");
        try {
            var response = invitations.inviteMember(new InviteMemberRequest("Invitee", "missing-key@example.test", Role.VIEWER), owner());
            assertFalse(response.getEmailDeliveryConfirmed());
            assertEquals(InvitationStatus.PENDING, repository.findById(response.getId()).orElseThrow().getStatus());
            verifyNoInteractions(client);
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "notificationInternalServiceKey", "invitation-test-key");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = InvitationStatus.class, names = {"PENDING", "EXPIRED"})
    void resendReplacesTokenAndCannotReviveRevokedOrAcceptedInvitation(InvitationStatus status) {
        var messages = new java.util.ArrayList<InvitationNotificationDto>();
        doAnswer(call -> { messages.add(call.getArgument(1)); return null; })
                .when(client).sendInvitationEmail(anyString(), any());
        var original = invitations.inviteMember(new InviteMemberRequest("Invitee", "resend@example.test", Role.DEVELOPER), owner());
        var previous = repository.findById(original.getId()).orElseThrow();
        previous.setStatus(status);
        repository.save(previous);
        var replacement = invitations.resendInvitation(original.getId(), owner());
        assertFalse(invitations.validateInvitation(token(messages.get(0))).isValid());
        assertTrue(invitations.validateInvitation(token(messages.get(1))).isValid());
        assertTrue(replacement.getEmailDeliveryConfirmed());
        assertFalse(new ObjectMapper().findAndRegisterModules().valueToTree(
                invitations.getAllInvitations(org.springframework.data.domain.Pageable.unpaged()).getContent())
                .toString().contains(token(messages.get(1))));
        invitations.revokeInvitation(replacement.getId(), owner());
        assertThrows(com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> invitations.resendInvitation(replacement.getId(), owner()));
        var accepted = repository.findById(replacement.getId()).orElseThrow();
        accepted.setStatus(InvitationStatus.ACCEPTED);
        repository.save(accepted);
        assertThrows(com.featureflag.auth_service.exception.InvalidOperationException.class,
                () -> invitations.resendInvitation(replacement.getId(), owner()));
        assertEquals(2, messages.size());
    }

    @Test
    void adminCannotReplaceOwnerIssuedAdminInvitation() {
        var adminInvite = invitations.inviteMember(
                new InviteMemberRequest("Admin Candidate", "admin-replace@example.test", Role.ADMIN),
                owner()
        );
        assertEquals(Role.ADMIN, adminInvite.getInvitedRole());
        assertEquals(InvitationStatus.PENDING, repository.findById(adminInvite.getId()).orElseThrow().getStatus());

        // Admin attempts replacement with VIEWER
        assertThrows(ForbiddenException.class, () ->
                invitations.inviteMember(
                        new InviteMemberRequest("Viewer Candidate", "admin-replace@example.test", Role.VIEWER),
                        admin()
                )
        );

        // Admin attempts replacement with DEVELOPER
        assertThrows(ForbiddenException.class, () ->
                invitations.inviteMember(
                        new InviteMemberRequest("Dev Candidate", "admin-replace@example.test", Role.DEVELOPER),
                        admin()
                )
        );

        // Original invitation must remain unchanged and PENDING
        var original = repository.findById(adminInvite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, original.getStatus());
        assertEquals(Role.ADMIN, original.getInvitedRole());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void adminCanReplacePermittedDeveloperInvitation() {
        var original = invitations.inviteMember(
                new InviteMemberRequest("Dev User", "dev-replace@example.test", Role.DEVELOPER),
                admin()
        );
        assertEquals(Role.DEVELOPER, original.getInvitedRole());

        var replacement = invitations.inviteMember(
                new InviteMemberRequest("Viewer User", "dev-replace@example.test", Role.VIEWER),
                admin()
        );
        assertEquals(Role.VIEWER, replacement.getInvitedRole());

        var originalInDb = repository.findById(original.getId()).orElseThrow();
        assertEquals(InvitationStatus.REVOKED, originalInDb.getStatus());

        var replacementInDb = repository.findById(replacement.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, replacementInDb.getStatus());
        assertEquals(Role.VIEWER, replacementInDb.getInvitedRole());
    }

    @Test
    void ownerCanReplaceAdminInvitation() {
        var original = invitations.inviteMember(
                new InviteMemberRequest("Admin User", "owner-admin@example.test", Role.ADMIN),
                owner()
        );
        assertEquals(Role.ADMIN, original.getInvitedRole());

        var replacement = invitations.inviteMember(
                new InviteMemberRequest("Admin User", "owner-admin@example.test", Role.ADMIN),
                owner()
        );
        assertEquals(Role.ADMIN, replacement.getInvitedRole());

        var originalInDb = repository.findById(original.getId()).orElseThrow();
        assertEquals(InvitationStatus.REVOKED, originalInDb.getStatus());

        var replacementInDb = repository.findById(replacement.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, replacementInDb.getStatus());
    }

    @Test
    void listAndValidateExpiredPendingInvitationDoesNotMutateDatabase() {
        var messages = new java.util.ArrayList<InvitationNotificationDto>();
        doAnswer(call -> { messages.add(call.getArgument(1)); return null; })
                .when(client).sendInvitationEmail(anyString(), any());

        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "expire-test@example.test", Role.DEVELOPER),
                owner()
        );
        var inDb = repository.findById(invite.getId()).orElseThrow();
        // Simulate expiry in past
        inDb.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
        repository.save(inDb);

        String rawToken = token(messages.get(0));

        // validateInvitation must report expired without modifying DB
        var validateRes = invitations.validateInvitation(rawToken);
        assertFalse(validateRes.isValid());
        assertTrue(validateRes.getErrorMessage().contains("expired"));

        var dbAfterValidate = repository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, dbAfterValidate.getStatus());

        // getAllInvitations must present EXPIRED without modifying DB
        var listRes = invitations.getAllInvitations(org.springframework.data.domain.Pageable.unpaged());
        var listed = listRes.getContent().stream()
                .filter(i -> i.getId().equals(invite.getId()))
                .findFirst().orElseThrow();
        assertEquals(InvitationStatus.EXPIRED, listed.getStatus());

        var dbAfterList = repository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.PENDING, dbAfterList.getStatus());
    }

    @Test
    void acceptedAndRevokedInvitationsRetainStateWhenPastExpiresAt() {
        var messages = new java.util.ArrayList<InvitationNotificationDto>();
        doAnswer(call -> { messages.add(call.getArgument(1)); return null; })
                .when(client).sendInvitationEmail(anyString(), any());

        var invite = invitations.inviteMember(
                new InviteMemberRequest("Candidate", "accepted-retain@example.test", Role.DEVELOPER),
                owner()
        );
        var inDb = repository.findById(invite.getId()).orElseThrow();
        inDb.setStatus(InvitationStatus.ACCEPTED);
        inDb.setAcceptedAt(java.time.Instant.now().minusSeconds(100));
        inDb.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
        repository.save(inDb);

        String rawToken = token(messages.get(0));

        var validateRes = invitations.validateInvitation(rawToken);
        assertFalse(validateRes.isValid());
        assertTrue(validateRes.getErrorMessage().contains("already been accepted"));

        var listRes = invitations.getAllInvitations(org.springframework.data.domain.Pageable.unpaged());
        var listed = listRes.getContent().stream()
                .filter(i -> i.getId().equals(invite.getId()))
                .findFirst().orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, listed.getStatus());

        var dbAfter = repository.findById(invite.getId()).orElseThrow();
        assertEquals(InvitationStatus.ACCEPTED, dbAfter.getStatus());
    }

    private User owner() {
        return User.builder().id(1L).name("Owner").email("owner@example.test").role(Role.OWNER).build();
    }

    private User admin() {
        return User.builder().id(2L).name("Admin").email("admin@example.test").role(Role.ADMIN).build();
    }

    private String token(InvitationNotificationDto request) {
        return java.net.URI.create(request.getAcceptanceUrl()).getRawQuery().substring("token=".length());
    }

    private void assertDeliveryResult(boolean confirmed) {
        var owner = User.builder().id(1L).name("Owner").email("owner@example.test").role(Role.OWNER).build();
        var request = new InviteMemberRequest("Invitee", "delivery-" + confirmed + "@example.test", Role.DEVELOPER);
        var response = invitations.inviteMember(request, owner);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
        assertTrue(json.has("emailDeliveryConfirmed"));
        assertEquals(confirmed, json.path("emailDeliveryConfirmed").asBoolean());
        assertEquals(InvitationStatus.PENDING, repository.findById(response.getId()).orElseThrow().getStatus());
        assertFalse(json.has("token"));
        assertFalse(json.has("tokenHash"));
        assertFalse(json.has("acceptanceUrl"));
        verify(client).sendInvitationEmail(eq("invitation-test-key"), any());
    }
}
