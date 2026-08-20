package com.featureflag.auth_service.service;

import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.InvitationNotificationDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationNotificationDispatcherTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private InvitationNotificationDispatcher dispatcher;

    private InvitationNotificationDto request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                dispatcher,
                "notificationInternalServiceKey",
                "test-internal-key"
        );

        request = InvitationNotificationDto.builder()
                .recipient("invitee@company.com")
                .inviteeName("Invitee")
                .inviterName("Owner")
                .inviterEmail("owner@company.com")
                .role("DEVELOPER")
                .expirationHours(48)
                .acceptanceUrl(
                        "https://frontend.example.test/accept-invitation?token=secret-token"
                )
                .build();
    }

    @AfterEach
    void cleanUpSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void dispatchIsDeferredUntilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(request);

        verifyNoInteractions(notificationClient);

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(notificationClient).sendInvitationEmail(
                "test-internal-key",
                request
        );
    }

    @Test
    void notificationFailureAfterCommitDoesNotEscapeToInvitationFlow() {
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationClient)
                .sendInvitationEmail("test-internal-key", request);

        assertDoesNotThrow(
                () -> dispatcher.dispatchAfterCommit(request)
        );
    }

    @Test
    void missingServiceKeySkipsRemoteCall() {
        ReflectionTestUtils.setField(
                dispatcher,
                "notificationInternalServiceKey",
                " "
        );

        assertDoesNotThrow(
                () -> dispatcher.dispatchAfterCommit(request)
        );

        verifyNoInteractions(notificationClient);
    }
}
