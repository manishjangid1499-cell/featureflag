package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
public class EvaluationTelemetryPublisher {

    public static final String DEFAULT_TOPIC =
            "feature-flag-evaluations";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Clock clock;
    private final FlagMetrics flagMetrics;

    public EvaluationTelemetryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value(
                    "${telemetry.evaluation.topic:"
                            + DEFAULT_TOPIC
                            + "}"
            )
            String topic,
            Clock clock,
            FlagMetrics flagMetrics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.clock = clock;
        this.flagMetrics = flagMetrics;
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
                clock.instant().toString()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            flagMetrics.telemetryPublished(false);
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

        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        topic,
                        messageKey,
                        payload
                );
        String correlationId = CorrelationIds.currentOrGenerate();
        record.headers().add(
                CorrelationIds.HEADER_NAME,
                correlationId.getBytes(StandardCharsets.UTF_8)
        );

        try {
            kafkaTemplate.send(
                    record
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    flagMetrics.telemetryPublished(false);
                    log.warn(
                            "Evaluation telemetry publish failed; "
                                    + "eventId={} flagKey={} "
                                    + "environment={} errorType={} "
                                    + "correlationId={}",
                            eventId,
                            evaluation.getFlagKey(),
                            evaluation.getEnvironment(),
                            exception.getClass().getSimpleName(),
                            correlationId
                    );
                } else {
                    flagMetrics.telemetryPublished(true);
                }
            });
        } catch (RuntimeException exception) {
            flagMetrics.telemetryPublished(false);
            log.warn(
                    "Evaluation telemetry enqueue failed; "
                            + "eventId={} flagKey={} environment={} "
                            + "errorType={} correlationId={}",
                    eventId,
                    evaluation.getFlagKey(),
                    evaluation.getEnvironment(),
                    exception.getClass().getSimpleName(),
                    correlationId
            );
        }
    }
}
