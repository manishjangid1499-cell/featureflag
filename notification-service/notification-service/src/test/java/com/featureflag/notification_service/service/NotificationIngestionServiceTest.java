package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.entity.DeliveryMode;
import com.featureflag.notification_service.entity.Notification;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationIngestionServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private NotificationIngestionService ingestionService;

    @Test
    void directEventCreatesOneDurablePendingJobAndMarker() {
        NotificationEvent event = directEvent();

        ingestionService.ingestDirectNotificationEvent(
                "event-direct-1",
                event
        );

        List<Notification> notifications =
                capturedNotifications();
        assertEquals(1, notifications.size());

        Notification notification = notifications.getFirst();
        assertEquals(
                "recipient@company.com",
                notification.getRecipient()
        );
        assertEquals(
                "creator@company.com",
                notification.getCreatorEmail()
        );
        assertEquals("Flag changed", notification.getSubject());
        assertEquals("A flag changed", notification.getMessage());
        assertEquals("EMAIL", notification.getType());
        assertDurablePendingState(notification);

        ProcessedEvent marker = capturedMarker();
        assertEquals("event-direct-1", marker.getEventId());
        assertEquals("notification-events", marker.getTopic());
        assertEquals(
                notification.getCreatedAt(),
                marker.getProcessedAt()
        );
        verify(processedEventRepository)
                .existsById("event-direct-1");
    }

    @Test
    void roleEventCreatesOneJobPerRecipientWithSharedTimestamp() {
        NotificationEvent event = roleEvent();
        List<String> recipients = List.of(
                "owner@company.com",
                "admin@company.com"
        );

        ingestionService.ingestRoleNotificationEvent(
                "event-role-1",
                event,
                recipients
        );

        List<Notification> notifications =
                capturedNotifications();
        assertEquals(2, notifications.size());
        assertEquals(
                recipients,
                notifications.stream()
                        .map(Notification::getRecipient)
                        .toList()
        );

        Notification first = notifications.getFirst();
        Notification second = notifications.get(1);
        assertDurablePendingState(first);
        assertDurablePendingState(second);
        assertNull(first.getCreatorEmail());
        assertNull(second.getCreatorEmail());
        assertEquals(first.getCreatedAt(), second.getCreatedAt());
        assertEquals(
                first.getNextAttemptAt(),
                second.getNextAttemptAt()
        );
        assertEquals(
                first.getCreatedAt(),
                capturedMarker().getProcessedAt()
        );
        verify(processedEventRepository)
                .existsById("event-role-1");
    }

    @Test
    void emptyRoleRecipientListCreatesMarkerWithoutNotifications() {
        ingestionService.ingestRoleNotificationEvent(
                "event-empty-role-1",
                roleEvent(),
                List.of()
        );

        verify(notificationRepository, never())
                .saveAll(any());
        ProcessedEvent marker = capturedMarker();
        assertEquals("event-empty-role-1", marker.getEventId());
        verify(processedEventRepository)
                .existsById("event-empty-role-1");
    }

    @Test
    void internalDuplicateCheckSkipsNotificationsAndSecondMarker() {
        when(
                processedEventRepository.existsById(
                        "event-duplicate-1"
                )
        ).thenReturn(true);

        ingestionService.ingestDirectNotificationEvent(
                "event-duplicate-1",
                directEvent()
        );

        verify(notificationRepository, never())
                .saveAll(any());
        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void notificationSaveFailurePropagatesBeforeMarkerSave() {
        RuntimeException saveFailure =
                new RuntimeException("notification save failed");
        doThrow(saveFailure)
                .when(notificationRepository)
                .saveAll(any());

        assertThatThrownBy(
                () -> ingestionService
                        .ingestDirectNotificationEvent(
                                "event-notification-failure-1",
                                directEvent()
                        )
        ).isSameAs(saveFailure);

        verify(processedEventRepository, never())
                .save(any(ProcessedEvent.class));
    }

    @Test
    void markerSaveFailurePropagates() {
        RuntimeException markerFailure =
                new RuntimeException("marker save failed");
        doThrow(markerFailure)
                .when(processedEventRepository)
                .save(any(ProcessedEvent.class));

        assertThatThrownBy(
                () -> ingestionService
                        .ingestDirectNotificationEvent(
                                "event-marker-failure-1",
                                directEvent()
                        )
        ).isSameAs(markerFailure);

        verify(notificationRepository).saveAll(any());
    }

    private void assertDurablePendingState(
            Notification notification
    ) {
        assertEquals(
                DeliveryMode.DURABLE,
                notification.getDeliveryMode()
        );
        assertEquals("PENDING", notification.getStatus());
        assertEquals(0, notification.getAttemptCount());
        assertEquals(
                notification.getCreatedAt(),
                notification.getNextAttemptAt()
        );
        assertNull(notification.getSentAt());
        assertNull(notification.getLastAttemptAt());
        assertNull(notification.getLeaseUntil());
        assertNull(notification.getClaimToken());
        assertNull(notification.getLastErrorType());
    }

    private List<Notification> capturedNotifications() {
        ArgumentCaptor<Iterable<Notification>> captor =
                iterableCaptor();
        verify(notificationRepository)
                .saveAll(captor.capture());

        return StreamSupport.stream(
                        captor.getValue().spliterator(),
                        false
                )
                .toList();
    }

    private ProcessedEvent capturedMarker() {
        ArgumentCaptor<ProcessedEvent> captor =
                ArgumentCaptor.forClass(
                        ProcessedEvent.class
                );
        verify(processedEventRepository)
                .save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<Iterable<Notification>>
    iterableCaptor() {
        return ArgumentCaptor.forClass(
                (Class) Iterable.class
        );
    }

    private NotificationEvent directEvent() {
        return new NotificationEvent(
                "event-direct-1",
                " recipient@company.com ",
                " creator@company.com ",
                "Flag changed",
                "A flag changed",
                null
        );
    }

    private NotificationEvent roleEvent() {
        return new NotificationEvent(
                "event-role-1",
                null,
                null,
                "Flag changed",
                "A flag changed",
                "EMAIL"
        );
    }
}
