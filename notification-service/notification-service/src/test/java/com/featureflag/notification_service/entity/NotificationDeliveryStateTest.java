package com.featureflag.notification_service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class NotificationDeliveryStateTest {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 8, 25, 12, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void builderDefaultsToSynchronousWithNoClaimedAttempts() {
        Notification notification = notificationBuilder().build();

        assertEquals(DeliveryMode.SYNCHRONOUS, notification.getDeliveryMode());
        assertEquals(0, notification.getAttemptCount());
    }

    @Test
    void builderCanExplicitlyCreateDurableNotification() {
        Notification notification = notificationBuilder()
                .deliveryMode(DeliveryMode.DURABLE)
                .build();

        assertEquals(DeliveryMode.DURABLE, notification.getDeliveryMode());
        assertEquals(0, notification.getAttemptCount());
    }

    @Test
    void internalDeliveryFieldsAreHiddenFromJson() {
        LocalDateTime nextAttemptAt = CREATED_AT.plusMinutes(1);
        Notification notification = notificationBuilder()
                .deliveryMode(DeliveryMode.DURABLE)
                .attemptCount(2)
                .nextAttemptAt(nextAttemptAt)
                .lastAttemptAt(CREATED_AT)
                .leaseUntil(CREATED_AT.plusMinutes(5))
                .claimToken("test-claim-token")
                .lastErrorType("MailSendException")
                .build();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode json = objectMapper.valueToTree(notification);

        assertTrue(json.has("recipient"));
        assertTrue(json.has("status"));
        assertFalse(json.has("deliveryMode"));
        assertFalse(json.has("attemptCount"));
        assertFalse(json.has("nextAttemptAt"));
        assertFalse(json.has("lastAttemptAt"));
        assertFalse(json.has("leaseUntil"));
        assertFalse(json.has("claimToken"));
        assertFalse(json.has("lastErrorType"));
    }

    @Test
    void internalDeliveryFieldsAreExcludedFromToStringAndEquality() {
        Notification first = notificationBuilder()
                .deliveryMode(DeliveryMode.SYNCHRONOUS)
                .attemptCount(0)
                .build();

        Notification second = notificationBuilder()
                .deliveryMode(DeliveryMode.DURABLE)
                .attemptCount(3)
                .nextAttemptAt(CREATED_AT.plusMinutes(1))
                .lastAttemptAt(CREATED_AT)
                .leaseUntil(CREATED_AT.plusMinutes(5))
                .claimToken("test-claim-token")
                .lastErrorType("MailSendException")
                .build();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(second.toString().contains("test-claim-token"));
        assertFalse(second.toString().contains("deliveryMode"));
        assertFalse(second.toString().contains("attemptCount"));
        assertFalse(second.toString().contains("nextAttemptAt"));
        assertFalse(second.toString().contains("lastAttemptAt"));
        assertFalse(second.toString().contains("leaseUntil"));
        assertFalse(second.toString().contains("claimToken"));
        assertFalse(second.toString().contains("lastErrorType"));
    }

    @Test
    void deliveryStateRoundTripsThroughJpaMapping() {
        LocalDateTime lastAttemptAt = CREATED_AT.plusMinutes(1);
        LocalDateTime nextAttemptAt = CREATED_AT.plusMinutes(2);
        LocalDateTime leaseUntil = CREATED_AT.plusMinutes(6);
        String claimToken = UUID.randomUUID().toString();

        Notification notification = notificationBuilder()
                .deliveryMode(DeliveryMode.DURABLE)
                .attemptCount(2)
                .nextAttemptAt(nextAttemptAt)
                .lastAttemptAt(lastAttemptAt)
                .leaseUntil(leaseUntil)
                .claimToken(claimToken)
                .lastErrorType("MailSendException")
                .build();

        Notification persisted = entityManager.persistAndFlush(notification);
        Long notificationId = persisted.getId();
        entityManager.clear();

        Notification reloaded = entityManager.find(
                Notification.class,
                notificationId
        );

        assertNotNull(reloaded);
        assertEquals(DeliveryMode.DURABLE, reloaded.getDeliveryMode());
        assertEquals(2, reloaded.getAttemptCount());
        assertEquals(nextAttemptAt, reloaded.getNextAttemptAt());
        assertEquals(lastAttemptAt, reloaded.getLastAttemptAt());
        assertEquals(leaseUntil, reloaded.getLeaseUntil());
        assertEquals(claimToken, reloaded.getClaimToken());
        assertEquals("MailSendException", reloaded.getLastErrorType());
    }

    private Notification.NotificationBuilder notificationBuilder() {
        return Notification.builder()
                .recipient("recipient@company.com")
                .creatorEmail("creator@company.com")
                .subject("Feature flag changed")
                .message("A feature flag changed")
                .type("EMAIL")
                .status("PENDING")
                .createdAt(CREATED_AT);
    }
}
