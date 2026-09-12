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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith({
        MockitoExtension.class,
        OutputCaptureExtension.class
})
class InvitationNotificationDispatcherTest {

    private static final String RECIPIENT =
            "distinct-invitee@company.com";

    private static final String RAW_TOKEN =
            "DISTINCT_AFTER_COMMIT_SECRET";

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private InvitationNotificationDispatcher dispatcher;

    @Mock
    private java.util.function.Consumer<Boolean> deliveryResult;

    private InvitationNotificationDto request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                dispatcher,
                "notificationInternalServiceKey",
                "test-internal-key"
        );

        request = InvitationNotificationDto.builder()
                .recipient(RECIPIENT)
                .inviteeName("Invitee")
                .inviterName("Owner")
                .inviterEmail("owner@company.com")
                .role("DEVELOPER")
                .expirationHours(48)
                .acceptanceUrl(
                        "https://frontend.example.test/accept-invitation?token="
                                + RAW_TOKEN
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
    void successfulDeliveryIsDeferredUntilAfterCommit(
            CapturedOutput output
    ) {
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(request, deliveryResult);

        verifyNoInteractions(notificationClient);

        verifyNoInteractions(deliveryResult);
        invokeAfterCommitCallbacks();
        verify(deliveryResult).accept(true);

        verify(notificationClient).sendInvitationEmail(
                "test-internal-key",
                request
        );
        verifyNoMoreInteractions(notificationClient);
        assertTrue(
                output.getOut().contains(
                        "Invitation email delivery confirmed by Notification Service"
                )
        );
    }

    @Test
    void notificationFailureFromActualAfterCommitCallbackIsContained(
            CapturedOutput output
    ) {
        String downstreamMessage =
                "downstream body contained private diagnostics";
        doThrow(new RuntimeException(downstreamMessage))
                .when(notificationClient)
                .sendInvitationEmail("test-internal-key", request);

        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(request, deliveryResult);

        verifyNoInteractions(notificationClient);
        assertDoesNotThrow(
                this::invokeAfterCommitCallbacks
        );

        verify(notificationClient, times(1)).sendInvitationEmail(
                "test-internal-key",
                request
        );
        verifyNoMoreInteractions(notificationClient);
        verify(deliveryResult).accept(false);
        assertTrue(output.getOut().contains("errorType=RuntimeException"));
        assertFalse(output.getOut().contains(RECIPIENT));
        assertFalse(output.getOut().contains(RAW_TOKEN));
        assertFalse(output.getOut().contains(request.getAcceptanceUrl()));
        assertFalse(output.getOut().contains(downstreamMessage));
    }

    @Test
    void missingServiceKeySkipsRemoteCall() {
        ReflectionTestUtils.setField(
                dispatcher,
                "notificationInternalServiceKey",
                " "
        );

        assertDoesNotThrow(
                () -> dispatcher.dispatchAfterCommit(request, deliveryResult)
        );

        verifyNoInteractions(notificationClient);
    }

    private void invokeAfterCommitCallbacks() {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
