package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.event.FlagEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class EvaluationTelemetryPublisher {

    public static final String DEFAULT_TOPIC =
            "feature-flag-evaluations";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public EvaluationTelemetryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value(
                    "${telemetry.evaluation.topic:"
                            + DEFAULT_TOPIC
                            + "}"
            )
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(FlagEvaluationResponse evaluation) {
        String eventId = UUID.randomUUID().toString();
        FlagEvent event = new FlagEvent(
                eventId,
                evaluation.isEnabled()
                        ? FlagEvent.EVALUATION_ENABLED
                        : FlagEvent.EVALUATION_DISABLED,
                evaluation.getFlagKey(),
                evaluation.getEnvironment(),
                LocalDateTime.now().toString()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Evaluation telemetry serialization failed; "
                            + "eventId={} flagKey={} environment={} "
                            + "errorType={}",
                    eventId,
                    evaluation.getFlagKey(),
                    evaluation.getEnvironment(),
                    exception.getClass().getSimpleName()
            );
            return;
        }

        String messageKey = evaluation.getFlagKey()
                + ":"
                + evaluation.getEnvironment();

        try {
            kafkaTemplate.send(
                    topic,
                    messageKey,
                    payload
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.warn(
                            "Evaluation telemetry publish failed; "
                                    + "eventId={} flagKey={} "
                                    + "environment={} errorType={}",
                            eventId,
                            evaluation.getFlagKey(),
                            evaluation.getEnvironment(),
                            exception.getClass().getSimpleName()
                    );
                }
            });
        } catch (RuntimeException exception) {
            log.warn(
                    "Evaluation telemetry enqueue failed; "
                            + "eventId={} flagKey={} environment={} "
                            + "errorType={}",
                    eventId,
                    evaluation.getFlagKey(),
                    evaluation.getEnvironment(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
