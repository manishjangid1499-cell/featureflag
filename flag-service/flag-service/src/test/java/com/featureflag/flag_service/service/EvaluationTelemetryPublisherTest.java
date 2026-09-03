package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationTelemetryPublisherTest {

    private static final Instant NOW =
            Instant.parse("2026-08-27T12:00:00Z");

    private static final String TOPIC =
            "feature-flag-evaluations";

    private final KafkaTemplate<String, String> kafkaTemplate =
            mock(KafkaTemplate.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlagMetrics flagMetrics = mock(FlagMetrics.class);

    private final EvaluationTelemetryPublisher publisher =
            new EvaluationTelemetryPublisher(
                    kafkaTemplate,
                    objectMapper,
                    TOPIC,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    flagMetrics
            );

    @Test
    void enabledEvaluationSerializesCompleteContractAndUsesStableKey()
            throws Exception {
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(successfulSend());

        publisher.publish(evaluation(true));

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> record =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());

        FlagEvent event = objectMapper.readValue(
                record.getValue().value(),
                FlagEvent.class
        );
        assertEquals(TOPIC, record.getValue().topic());
        assertEquals("checkout:DEV", record.getValue().key());
        assertNotNull(record.getValue().headers()
                .lastHeader(CorrelationIds.HEADER_NAME));
        assertNotNull(UUID.fromString(event.getEventId()));
        assertEquals(
                FlagEvent.EVALUATION_ENABLED,
                event.getEventType()
        );
        assertEquals("checkout", event.getFlagKey());
        assertEquals("DEV", event.getEnvironment());
        assertFalse(event.getTimestamp().isBlank());
        assertEquals(NOW.toString(), event.getTimestamp());
        verify(flagMetrics).telemetryPublished(true);
    }

    @Test
    void disabledEvaluationUsesDisabledEventType() throws Exception {
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(successfulSend());

        publisher.publish(evaluation(false));

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> record =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        FlagEvent event = objectMapper.readValue(
                record.getValue().value(),
                FlagEvent.class
        );
        assertEquals(
                FlagEvent.EVALUATION_DISABLED,
                event.getEventType()
        );
    }

    @Test
    void synchronousKafkaFailureIsIsolated() {
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenThrow(new RuntimeException("broker unavailable"));

        assertDoesNotThrow(() -> publisher.publish(evaluation(true)));
        verify(flagMetrics).telemetryPublished(false);
    }

    @Test
    void asynchronousKafkaFailureIsIsolated() {
        CompletableFuture<SendResult<String, String>> failure =
                new CompletableFuture<>();
        failure.completeExceptionally(
                new RuntimeException("broker rejected send")
        );
        when(
                kafkaTemplate.send(any(ProducerRecord.class))
        ).thenReturn(failure);

        assertDoesNotThrow(() -> publisher.publish(evaluation(false)));
        verify(flagMetrics).telemetryPublished(false);
    }

    @Test
    void serializationFailureIsIsolatedBeforeKafkaSend()
            throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        EvaluationTelemetryPublisher failingPublisher =
                new EvaluationTelemetryPublisher(
                        kafkaTemplate,
                        failingMapper,
                        TOPIC,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        flagMetrics
                );
        when(failingMapper.writeValueAsString(
                org.mockito.ArgumentMatchers.any(FlagEvent.class)
        )).thenThrow(new JsonProcessingException("serialization failed") {
        });

        assertDoesNotThrow(
                () -> failingPublisher.publish(evaluation(true))
        );
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(flagMetrics).telemetryPublished(false);
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(
                mock(SendResult.class)
        );
    }

    private FlagEvaluationResponse evaluation(boolean enabled) {
        return new FlagEvaluationResponse(
                "checkout",
                "DEV",
                enabled,
                false,
                enabled ? 100 : 0,
                null,
                null,
                true
        );
    }
}
