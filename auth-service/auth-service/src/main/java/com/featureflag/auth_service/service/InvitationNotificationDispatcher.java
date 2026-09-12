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

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationNotificationDispatcher {

    private final NotificationClient notificationClient;

    @Value("${NOTIFICATION_INTERNAL_SERVICE_KEY:}")
    private String notificationInternalServiceKey;

    public void dispatchAfterCommit(InvitationNotificationDto request, Consumer<Boolean> deliveryResult) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deliveryResult.accept(dispatch(request));
                        }
                    }
            );
            return;
        }

        log.warn("No transaction synchronization is active; dispatching invitation notification immediately");
        deliveryResult.accept(dispatch(request));
    }

    private boolean dispatch(InvitationNotificationDto request) {
        if (!StringUtils.hasText(notificationInternalServiceKey)) {
            log.error("Invitation notification dispatch skipped because internal service authentication is not configured");
            return false;
        }

        try {
            notificationClient.sendInvitationEmail(
                    notificationInternalServiceKey,
                    request
            );
            log.info("Invitation email delivery confirmed by Notification Service");
            return true;
        } catch (Exception exception) {
            // The invitation has already committed at this point. Do not expose the
            // raw acceptance URL or exception message in logs.
            log.error(
                    "Invitation email delivery failed after invitation commit; errorType={} causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName()
            );
            return false;
        }
    }
}
