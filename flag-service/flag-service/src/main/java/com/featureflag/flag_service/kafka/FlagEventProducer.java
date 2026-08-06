package com.featureflag.flag_service.kafka;

import com.featureflag.flag_service.event.FlagEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlagEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(FlagEvent event) {

        kafkaTemplate.send(
                "feature-flag-events",
                event
        );

        System.out.println(
                "Kafka Event Published: "
                        + event.getEventType()
        );
    }
}