package com.featureflag.audit_service.config;

import com.featureflag.audit_service.observability.KafkaFailureVisibility;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOffExecution;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaRetryPolicyTest {
    @Test
    void wrappedInfrastructureFailuresReceiveLongerButFiniteRetries() {
        for (Exception failure : List.of(new DataAccessResourceFailureException("database unavailable"),
                new CannotCreateTransactionException("connection unavailable"))) {
            var backOff = KafkaConfig.retryBackOff(new ListenerExecutionFailedException("listener", failure)).start();
            long totalBackoff = 0;
            for (int retry = 0; retry < 12; retry++) {
                long delay = backOff.nextBackOff();
                assertThat(delay).isEqualTo(5000);
                totalBackoff += delay;
            }
            assertThat(totalBackoff).isEqualTo(60000);
            assertThat(backOff.nextBackOff()).isEqualTo(BackOffExecution.STOP);
        }
        var unexpected = KafkaConfig.retryBackOff(new IllegalStateException("unexpected")).start();
        assertThat(unexpected.nextBackOff()).isEqualTo(1000);
        assertThat(unexpected.nextBackOff()).isEqualTo(1000);
        assertThat(unexpected.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void poisonContractGoesToDltOnFirstFailure() {
        KafkaTemplate kafka = mock(KafkaTemplate.class);
        KafkaFailureVisibility visibility = mock(KafkaFailureVisibility.class);
        when(visibility.dltDestination(any(), any(), eq(KafkaConfig.DLT_TOPIC)))
                .thenReturn(new TopicPartition(KafkaConfig.DLT_TOPIC, 0));
        when(kafka.partitionsFor(KafkaConfig.DLT_TOPIC)).thenReturn(List.of(
                new PartitionInfo(KafkaConfig.DLT_TOPIC, 0, null, new Node[0], new Node[0])));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = new KafkaConfig(new KafkaProperties()).kafkaErrorHandler(kafka, visibility);
        var failure = new ListenerExecutionFailedException("listener", new IllegalArgumentException("invalid contract"));
        assertThat(handler.handleOne(failure, new ConsumerRecord<>("source", 0, 0, "key", "test-event"),
                mock(Consumer.class), mock(MessageListenerContainer.class))).isTrue();
        verify(kafka).send(any(ProducerRecord.class));
    }
}
