package com.featureflag.notification_service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class NotificationDeliveryStateTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-25T12:00:00Z");

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
        Instant nextAttemptAt = CREATED_AT.plusSeconds(60);
        Notification notification = notificationBuilder()
                .deliveryMode(DeliveryMode.DURABLE)
                .attemptCount(2)
                .nextAttemptAt(nextAttemptAt)
                .lastAttemptAt(CREATED_AT)
                .leaseUntil(CREATED_AT.plusSeconds(5 * 60))
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
                .nextAttemptAt(CREATED_AT.plusSeconds(60))
                .lastAttemptAt(CREATED_AT)
                .leaseUntil(CREATED_AT.plusSeconds(5 * 60))
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
        Instant lastAttemptAt = CREATED_AT.plusSeconds(60);
        Instant nextAttemptAt = CREATED_AT.plusSeconds(2 * 60);
        Instant leaseUntil = CREATED_AT.plusSeconds(6 * 60);
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
