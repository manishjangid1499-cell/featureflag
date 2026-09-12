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

    private User owner() {
        return User.builder().id(1L).name("Owner").email("owner@example.test").role(Role.OWNER).build();
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
