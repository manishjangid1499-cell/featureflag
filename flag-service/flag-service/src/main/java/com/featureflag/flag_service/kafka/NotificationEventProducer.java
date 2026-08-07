package com.featureflag.flag_service.kafka;

import com.featureflag.flag_service.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishNotification(NotificationEvent event) {

        kafkaTemplate.send(
                "notification-events",
                event
        );

        System.out.println(
                "Notification Event Published: "
                        + event.getSubject()
        );
    }
}