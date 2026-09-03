package com.featureflag.analytics_service.aggregation;

import com.featureflag.analytics_service.entity.AnalyticsEvent;
import com.featureflag.analytics_service.entity.ProcessedEvent;
import com.featureflag.analytics_service.event.FlagEvent;
import com.featureflag.analytics_service.kafka.AnalyticsEventConsumer;
import com.featureflag.analytics_service.repository.AnalyticsEventRepository;
import com.featureflag.analytics_service.repository.ProcessedEventRepository;
import com.featureflag.analytics_service.observability.AnalyticsMetrics;
import com.featureflag.analytics_service.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AnalyticsAggregationConcurrencyMySqlIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.hikari.maximum-pool-size=24",
                "spring.main.banner-mode=off"
        }
)
@ActiveProfiles("mysql-it")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class AnalyticsAggregationConcurrencyMySqlIT {

    @MockitoBean
    private AnalyticsMetrics analyticsMetrics;

    private static final Logger LOG = LoggerFactory.getLogger(
            AnalyticsAggregationConcurrencyMySqlIT.class
    );

    private static final int CONCURRENT_EVENT_COUNT = 20;
    private static final String ENVIRONMENT = "DEV";
    private static final String EVENT_TYPE = "FLAG_EVALUATED";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11");

    private final AnalyticsEventConsumer consumer;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AnalyticsAggregationConcurrencyMySqlIT(
            AnalyticsEventConsumer consumer,
            JdbcTemplate jdbcTemplate
    ) {
        this.consumer = consumer;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearBusinessData() {
        jdbcTemplate.update("DELETE FROM processed_kafka_events");
        jdbcTemplate.update("DELETE FROM analytics_events");
    }

    @RepeatedTest(3)
    void distinctEventsIncrementOneAggregateWithoutLostUpdates(
            RepetitionInfo repetitionInfo
    ) throws Exception {
        String repetition = Integer.toString(
                repetitionInfo.getCurrentRepetition()
        );
        String flagKey = "concurrent-checkout-" + repetition;
        List<FlagEvent> events = new ArrayList<>();
        for (int index = 0; index < CONCURRENT_EVENT_COUNT; index++) {
            events.add(event(
                    "distinct-" + repetition + "-" + index,
                    flagKey
            ));
        }

        List<Throwable> outcomes = invokeConcurrently(events);

        assertTrue(
                outcomes.stream().allMatch(outcome -> outcome == null),
                () -> "Unexpected concurrent failures: " + outcomes
        );
        assertAggregateState(flagKey, 1, CONCURRENT_EVENT_COUNT);
        assertEquals(
                CONCURRENT_EVENT_COUNT,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE event_id LIKE ?
                        """,
                        Integer.class,
                        "distinct-" + repetition + "-%"
                )
        );
    }

    @Test
    void concurrentSameEventIdIncrementsExactlyOnce() throws Exception {
        String eventId = "same-event-id";
        String flagKey = "same-event-checkout";

        List<Throwable> outcomes = invokeConcurrently(List.of(
                event(eventId, flagKey),
                event(eventId, flagKey)
        ));
        List<Throwable> failures = outcomes.stream()
                .filter(outcome -> outcome != null)
                .toList();

        assertTrue(
                failures.stream().allMatch(this::isIntegrityFailure),
                () -> "Unexpected same-event failures: " + failures
        );
        assertTrue(
                outcomes.stream().anyMatch(outcome -> outcome == null),
                "At least one delivery must commit"
        );
        assertAggregateState(flagKey, 1, 1);
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM processed_kafka_events
                        WHERE event_id = ?
                        """,
                        Integer.class,
                        eventId
                )
        );

        LOG.info(
                "Concurrent same-event result: successes={} integrityFailures={}",
                outcomes.size() - failures.size(),
                failures.size()
        );
    }

    @Test
    void markerFailureRollsBackAggregateIncrement() {
        String flagKey = "rollback-checkout";
        FlagEvent event = event("x".repeat(65), flagKey);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> consumer.consume(event)
        );

        assertTrue(
                isIntegrityFailure(failure),
                () -> "Expected marker integrity failure but got " + failure
        );
        assertAggregateState(flagKey, 0, null);
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_kafka_events",
                        Integer.class
                )
        );
    }

    private List<Throwable> invokeConcurrently(
            List<FlagEvent> events
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(events.size());
        CountDownLatch ready = new CountDownLatch(events.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        try {
            for (FlagEvent event : events) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        consumer.consume(event);
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                }));
            }

            assertTrue(
                    ready.await(30, TimeUnit.SECONDS),
                    "Concurrent workers did not reach the start barrier"
            );
            start.countDown();

            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(30, TimeUnit.SECONDS),
                    "Concurrent executor did not terminate"
            );
        }
    }

    private void assertAggregateState(
            String flagKey,
            int expectedRows,
            Integer expectedCount
    ) {
        assertEquals(
                expectedRows,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM analytics_events
                        WHERE flag_key = ?
                          AND environment = ?
                          AND event_type = ?
                        """,
                        Integer.class,
                        flagKey,
                        ENVIRONMENT,
                        EVENT_TYPE
                )
        );

        if (expectedCount != null) {
            Long actualCount = jdbcTemplate.queryForObject(
                    """
                    SELECT count
                    FROM analytics_events
                    WHERE flag_key = ?
                      AND environment = ?
                      AND event_type = ?
                    """,
                    Long.class,
                    flagKey,
                    ENVIRONMENT,
                    EVENT_TYPE
            );
            assertNotNull(actualCount);
            assertEquals(expectedCount.longValue(), actualCount);
        }
    }

    private boolean isIntegrityFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("23")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private FlagEvent event(String eventId, String flagKey) {
        FlagEvent event = new FlagEvent();
        event.setEventId(eventId);
        event.setEventType(EVENT_TYPE);
        event.setFlagKey(flagKey);
        event.setEnvironment(ENVIRONMENT);
        event.setTimestamp("2026-08-27T12:00:00Z");
        return event;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
    @EntityScan(basePackageClasses = {
            AnalyticsEvent.class,
            ProcessedEvent.class
    })
    @EnableJpaRepositories(basePackageClasses = {
            AnalyticsEventRepository.class,
            ProcessedEventRepository.class
    })
    @Import({
            AnalyticsService.class,
            AnalyticsEventConsumer.class
    })
    static class TestApplication {
    }
}
