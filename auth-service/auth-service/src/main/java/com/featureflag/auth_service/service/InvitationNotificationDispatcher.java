package com.featureflag.auth_service.service;

import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.InvitationNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationNotificationDispatcher {

    private final NotificationClient notificationClient;

    @Value("${NOTIFICATION_INTERNAL_SERVICE_KEY:}")
    private String notificationInternalServiceKey;

    public void dispatchAfterCommit(InvitationNotificationDto request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatch(request);
                        }
                    }
            );
            return;
        }

        log.warn("No transaction synchronization is active; dispatching invitation notification immediately");
        dispatch(request);
    }

    private void dispatch(InvitationNotificationDto request) {
        if (!StringUtils.hasText(notificationInternalServiceKey)) {
            log.error("Invitation notification dispatch skipped because internal service authentication is not configured");
            return;
        }

        try {
            notificationClient.sendInvitationEmail(
                    notificationInternalServiceKey,
                    request
            );
            log.info("Invitation notification handoff accepted by Notification Service");
        } catch (Exception exception) {
            // The invitation has already committed at this point. Do not expose the
            // raw acceptance URL or exception message in logs.
            log.error(
                    "Invitation notification handoff failed after commit; errorType={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
