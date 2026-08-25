package com.featureflag.notification_service.service;

import com.featureflag.notification_service.dto.NotificationEvent;
import com.featureflag.notification_service.entity.ProcessedEvent;
import com.featureflag.notification_service.exception.UnsupportedNotificationChannelException;
import com.featureflag.notification_service.repository.NotificationRepository;
import com.featureflag.notification_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@ActiveProfiles("test")
@Import(NotificationIngestionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationIngestionServiceIntegrationTest {

    @Autowired
    private NotificationIngestionService ingestionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private ProcessedEventRepository processedEventRepository;

    @Test
    void markerFailureRollsBackPreviouslyInsertedNotification() {
        DataIntegrityViolationException markerFailure =
                new DataIntegrityViolationException(
                        "simulated marker failure"
                );
        doAnswer(invocation -> {
            assertEquals(1, notificationRepository.count());
            throw markerFailure;
        }).when(processedEventRepository)
                .save(any(ProcessedEvent.class));

        assertThatThrownBy(
                () -> ingestionService
                        .ingestDirectNotificationEvent(
                                "event-rollback-1",
                                directEvent()
                        )
        ).isSameAs(markerFailure);

        assertEquals(0, notificationRepository.count());
        assertEquals(0, processedEventRepository.count());
    }

    @Test
    void unsupportedTypeCommitsNeitherNotificationNorMarker() {
        NotificationEvent event = directEvent();
        event.setType("SMS");

        assertThatThrownBy(
                () -> ingestionService
                        .ingestDirectNotificationEvent(
                                "event-unsupported-1",
                                event
                        )
        ).isInstanceOf(
                UnsupportedNotificationChannelException.class
        );

        assertEquals(0, notificationRepository.count());
        assertEquals(0, processedEventRepository.count());
    }

    private NotificationEvent directEvent() {
        return new NotificationEvent(
                "event-rollback-1",
                "recipient@company.com",
                "creator@company.com",
                "Flag changed",
                "A flag changed",
                "EMAIL"
        );
    }
}
