package com.featureflag.auth_service.service;

import com.featureflag.auth_service.client.NotificationClient;
import com.featureflag.auth_service.dto.PasswordResetNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;

@Service
@Slf4j
public class PasswordResetNotificationDispatcher {

    private final NotificationClient notificationClient;

    @Value("${NOTIFICATION_INTERNAL_SERVICE_KEY:}")
    private String notificationInternalServiceKey;

    // Bounded executor: max 4 threads, queue of 256. Prevents public endpoint blocking on HTTP/SMTP latency.
    private final ExecutorService executorService;

    public PasswordResetNotificationDispatcher(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
        this.executorService = new ThreadPoolExecutor(
                2,
                4,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public synchronized Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "pwd-reset-dispatcher-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void dispatchAfterCommit(PasswordResetNotificationDto request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submitAsyncDispatch(request);
                        }
                    }
            );
            return;
        }

        log.warn("No transaction synchronization is active; dispatching password reset notification immediately");
        submitAsyncDispatch(request);
    }

    private void submitAsyncDispatch(PasswordResetNotificationDto request) {
        try {
            executorService.submit(() -> dispatch(request));
        } catch (RejectedExecutionException rex) {
            log.error("Password reset notification queue is full; rejected async delivery: errorType={}", rex.getClass().getSimpleName());
        }
    }

    private void dispatch(PasswordResetNotificationDto request) {
        if (!StringUtils.hasText(notificationInternalServiceKey)) {
            log.error("Password reset notification dispatch skipped because internal service authentication is not configured");
            return;
        }

        try {
            notificationClient.sendPasswordResetEmail(
                    notificationInternalServiceKey,
                    request
            );
            log.info("Password reset email delivery confirmed by Notification Service");
        } catch (Exception exception) {
            // The password recovery token has already committed. Do not log recipient,
            // raw reset URL, or exception message which could leak tokens.
            log.error(
                    "Password reset email delivery failed after commit; errorType={} causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName()
            );
        }
    }
}
