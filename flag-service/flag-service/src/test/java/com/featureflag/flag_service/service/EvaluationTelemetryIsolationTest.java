package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.config.KafkaConfig;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.observability.CorrelationIds;
import com.featureflag.flag_service.observability.FlagMetrics;
import com.featureflag.flag_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationTelemetryIsolationTest {
    @Test
    void blockedKafkaCannotBlockDecisionsAndFullQueueDropsImmediately() throws Exception {
        ThreadPoolExecutor executor = new KafkaConfig().evaluationTelemetryExecutor(1);
        CountDownLatch enteredSend = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            assertThat(new String(record.headers().lastHeader(CorrelationIds.HEADER_NAME).value(),
                    StandardCharsets.UTF_8)).isEqualTo("isolation-test");
            enteredSend.countDown();
            if (!releaseSend.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release producer");
            }
            sent.countDown();
            return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        });
        FlagMetrics metrics = new FlagMetrics(registry, mock(OutboxEventRepository.class));
        EvaluationTelemetryPublisher publisher = new EvaluationTelemetryPublisher(kafka,
                new ObjectMapper(), "feature-flag-evaluations", Clock.systemUTC(), metrics, executor);
        FlagService flags = mock(FlagService.class);
        FlagEvaluationResponse decision = new FlagEvaluationResponse("checkout", "DEV", true,
                false, 100, null, null, true);
        when(flags.evaluateFlag("checkout", "user", "DEV")).thenReturn(decision);
        FlagEvaluationTelemetryService service = new FlagEvaluationTelemetryService(flags, publisher, metrics);
        // Load assertion/timer classes outside the bounded request-path checks.
        assertThat(decision).isNotNull();
        metrics.startEvaluation();
        try {
            // The producer remains blocked until AFTER all three HTTP-path evaluations return.
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> evaluate(service, decision));
            assertThat(enteredSend.await(2, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                evaluate(service, decision);
                evaluate(service, decision);
            });
            assertThat(executor.getQueue()).hasSize(1);
            assertThat(registry.get("feature.flag.telemetry.admission")
                    .tag("outcome", "accepted").counter().count()).isEqualTo(2);
            assertThat(registry.get("feature.flag.telemetry.admission")
                    .tag("outcome", "dropped").counter().count()).isEqualTo(1);
            assertThat(registry.get("feature.flag.runtime.evaluation.latency")
                    .tag("result", "enabled").timer().count()).isEqualTo(3);
            releaseSend.countDown();
            assertThat(sent.await(3, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.get("feature.flag.telemetry.publish")
                    .tag("outcome", "failure").counter().count()).isEqualTo(2);
            evaluate(service, decision);
            assertThat(registry.get("feature.flag.telemetry.admission")
                    .tag("outcome", "dropped").counter().count()).isEqualTo(2);
        } finally {
            releaseSend.countDown();
            executor.shutdownNow();
            registry.close();
        }
    }

    private void evaluate(FlagEvaluationTelemetryService service, FlagEvaluationResponse decision) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIds.MDC_KEY, "isolation-test")) {
            assertThat(service.evaluateFlag("checkout", "user", "DEV")).isSameAs(decision);
        }
    }
}
