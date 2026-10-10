package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagAuditSnapshot;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagScheduleTimezoneTest {

    @Mock
    private FeatureFlagRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private OutboxService outboxService;

    private ObjectMapper objectMapper;
    private TimeZone originalJvmTimeZone;

    private static final Instant START = Instant.parse("2026-10-06T12:00:00Z");
    private static final Instant END = Instant.parse("2026-10-06T14:00:00Z");

    @BeforeEach
    void setUp() {
        originalJvmTimeZone = TimeZone.getDefault();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalJvmTimeZone);
    }

    private FeatureFlag createScheduledFlag() {
        return FeatureFlag.builder()
                .id(1L)
                .name("Scheduled Flag")
                .flagKey("scheduled-flag")
                .environment("PROD")
                .enabled(true)
                .rolloutPercentage(100)
                .startDate(START)
                .endDate(END)
                .targetUsers(List.of())
                .build();
    }

    @Test
    @DisplayName("Backend evaluation is identical across diverse JVM default timezones")
    void evaluationIsIdenticalAcrossJvmDefaultTimezones() {
        FeatureFlag flag = createScheduledFlag();
        when(repository.findByFlagKeyAndEnvironment("scheduled-flag", "PROD"))
                .thenReturn(Optional.of(flag));

        // Test at instant 13:00:00Z (within window)
        Instant evalInstant = Instant.parse("2026-10-06T13:00:00Z");
        Clock fixedClock = Clock.fixed(evalInstant, ZoneOffset.UTC);
        FlagService service = new FlagService(repository, redisTemplate, objectMapper, outboxService, fixedClock);

        List<String> testTimeZones = List.of(
                "UTC",
                "Asia/Kolkata",
                "America/New_York",
                "Europe/London",
                "Pacific/Auckland",
                "Australia/Sydney"
        );

        for (String tzId : testTimeZones) {
            TimeZone.setDefault(TimeZone.getTimeZone(tzId));

            FlagEvaluationResponse response = service.evaluateFlag("scheduled-flag", "user-1", "PROD");
            assertTrue(response.isWithinSchedule(), "Must be within schedule in timezone " + tzId);
            assertTrue(response.isEnabled(), "Must be enabled in timezone " + tzId);
            assertEquals(START, response.getStartDate(), "StartDate must be preserved as UTC Instant");
            assertEquals(END, response.getEndDate(), "EndDate must be preserved as UTC Instant");
        }
    }

    @Test
    @DisplayName("Inclusive boundary evaluation: exact start and end boundaries are active")
    void boundarySemanticsPreserved() {
        FeatureFlag flag = createScheduledFlag();
        when(repository.findByFlagKeyAndEnvironment("scheduled-flag", "PROD"))
                .thenReturn(Optional.of(flag));

        // 1. One millisecond before start -> inactive
        Clock beforeClock = Clock.fixed(START.minusMillis(1), ZoneOffset.UTC);
        FlagService beforeService = new FlagService(repository, redisTemplate, objectMapper, outboxService, beforeClock);
        assertFalse(beforeService.evaluateFlag("scheduled-flag", "user-1", "PROD").isWithinSchedule());

        // 2. Exactly at start boundary -> active (inclusive)
        Clock atStartClock = Clock.fixed(START, ZoneOffset.UTC);
        FlagService atStartService = new FlagService(repository, redisTemplate, objectMapper, outboxService, atStartClock);
        assertTrue(atStartService.evaluateFlag("scheduled-flag", "user-1", "PROD").isWithinSchedule());

        // 3. In the middle -> active
        Clock middleClock = Clock.fixed(Instant.parse("2026-10-06T13:00:00Z"), ZoneOffset.UTC);
        FlagService middleService = new FlagService(repository, redisTemplate, objectMapper, outboxService, middleClock);
        assertTrue(middleService.evaluateFlag("scheduled-flag", "user-1", "PROD").isWithinSchedule());

        // 4. Exactly at end boundary -> active (inclusive)
        Clock atEndClock = Clock.fixed(END, ZoneOffset.UTC);
        FlagService atEndService = new FlagService(repository, redisTemplate, objectMapper, outboxService, atEndClock);
        assertTrue(atEndService.evaluateFlag("scheduled-flag", "user-1", "PROD").isWithinSchedule());

        // 5. One millisecond after end -> inactive
        Clock afterClock = Clock.fixed(END.plusMillis(1), ZoneOffset.UTC);
        FlagService afterService = new FlagService(repository, redisTemplate, objectMapper, outboxService, afterClock);
        assertFalse(afterService.evaluateFlag("scheduled-flag", "user-1", "PROD").isWithinSchedule());
    }

    @Test
    @DisplayName("Null start and end boundaries mean unbounded")
    void nullBoundariesMeanUnbounded() {
        // Unbounded start: active anytime up to END
        FeatureFlag unboundedStart = FeatureFlag.builder()
                .id(2L).flagKey("unbounded-start").environment("PROD")
                .enabled(true).rolloutPercentage(100)
                .startDate(null).endDate(END).build();
        when(repository.findByFlagKeyAndEnvironment("unbounded-start", "PROD"))
                .thenReturn(Optional.of(unboundedStart));

        Clock clockBefore = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        FlagService s1 = new FlagService(repository, redisTemplate, objectMapper, outboxService, clockBefore);
        assertTrue(s1.evaluateFlag("unbounded-start", "user-1", "PROD").isWithinSchedule());

        Clock clockAfter = Clock.fixed(END.plusSeconds(10), ZoneOffset.UTC);
        FlagService s2 = new FlagService(repository, redisTemplate, objectMapper, outboxService, clockAfter);
        assertFalse(s2.evaluateFlag("unbounded-start", "user-1", "PROD").isWithinSchedule());

        // Unbounded end: active anytime after START
        FeatureFlag unboundedEnd = FeatureFlag.builder()
                .id(3L).flagKey("unbounded-end").environment("PROD")
                .enabled(true).rolloutPercentage(100)
                .startDate(START).endDate(null).build();
        when(repository.findByFlagKeyAndEnvironment("unbounded-end", "PROD"))
                .thenReturn(Optional.of(unboundedEnd));

        FlagService s3 = new FlagService(repository, redisTemplate, objectMapper, outboxService, clockBefore);
        assertFalse(s3.evaluateFlag("unbounded-end", "user-1", "PROD").isWithinSchedule());

        Clock clockWayAfter = Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC);
        FlagService s4 = new FlagService(repository, redisTemplate, objectMapper, outboxService, clockWayAfter);
        assertTrue(s4.evaluateFlag("unbounded-end", "user-1", "PROD").isWithinSchedule());

        // Completely unbounded
        FeatureFlag unboundedBoth = FeatureFlag.builder()
                .id(4L).flagKey("unbounded-both").environment("PROD")
                .enabled(true).rolloutPercentage(100)
                .startDate(null).endDate(null).build();
        when(repository.findByFlagKeyAndEnvironment("unbounded-both", "PROD"))
                .thenReturn(Optional.of(unboundedBoth));

        FlagService s5 = new FlagService(repository, redisTemplate, objectMapper, outboxService, clockBefore);
        assertTrue(s5.evaluateFlag("unbounded-both", "user-1", "PROD").isWithinSchedule());
    }

    @Test
    @DisplayName("Invalid schedule with endDate before startDate is rejected")
    void invalidReversedScheduleIsRejected() {
        FlagService service = new FlagService(repository, redisTemplate, objectMapper, outboxService, Clock.systemUTC());

        FlagRequest request = new FlagRequest();
        request.setName("Reversed Flag");
        request.setFlagKey("reversed-flag");
        request.setEnvironment("DEV");
        request.setEnabled(true);
        request.setStartDate(Instant.parse("2026-10-06T15:00:00Z"));
        request.setEndDate(Instant.parse("2026-10-06T10:00:00Z"));

        assertThrows(IllegalArgumentException.class, () -> service.createFlag(request));
    }

    @Test
    @DisplayName("Legacy Redis JSON without timezone offset is parsed safely as UTC Instant")
    void legacyRedisJsonWithoutTimezoneParsesAsUtcInstant() throws Exception {
        String legacyJson = """
                {
                    "id": 10,
                    "flagKey": "legacy-flag",
                    "name": "Legacy Flag",
                    "environment": "DEV",
                    "enabled": true,
                    "rolloutPercentage": 100,
                    "startDate": "2026-10-06T12:30:00",
                    "endDate": "2026-10-06T18:00:00"
                }
                """;

        FeatureFlag flag = objectMapper.readValue(legacyJson, FeatureFlag.class);
        assertThat(flag.getStartDate()).isEqualTo(Instant.parse("2026-10-06T12:30:00Z"));
        assertThat(flag.getEndDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));
    }

    @Test
    @DisplayName("New Redis JSON with ISO-8601 UTC Z is parsed accurately into Instant")
    void modernRedisJsonWithZParsesAccurately() throws Exception {
        String modernJson = """
                {
                    "id": 11,
                    "flagKey": "modern-flag",
                    "name": "Modern Flag",
                    "environment": "DEV",
                    "enabled": true,
                    "rolloutPercentage": 100,
                    "startDate": "2026-10-06T12:30:45.123456Z",
                    "endDate": "2026-10-06T18:00:00Z"
                }
                """;

        FeatureFlag flag = objectMapper.readValue(modernJson, FeatureFlag.class);
        assertThat(flag.getStartDate()).isEqualTo(Instant.parse("2026-10-06T12:30:45.123456Z"));
        assertThat(flag.getEndDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));
    }

    @Test
    @DisplayName("FlagAuditSnapshot deserializes both legacy timezone-less strings and modern UTC Z strings")
    void auditSnapshotCompatibility() throws Exception {
        String legacySnapshotJson = """
                {
                    "id": 1,
                    "flagKey": "audit-flag",
                    "name": "Audit Flag",
                    "environment": "DEV",
                    "enabled": true,
                    "rolloutPercentage": 50,
                    "startDate": "2026-10-06T12:30:00",
                    "endDate": "2026-10-06T18:00:00"
                }
                """;
        FlagAuditSnapshot legacySnapshot = objectMapper.readValue(legacySnapshotJson, FlagAuditSnapshot.class);
        assertThat(legacySnapshot.startDate()).isEqualTo(Instant.parse("2026-10-06T12:30:00Z"));
        assertThat(legacySnapshot.endDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));

        String modernSnapshotJson = """
                {
                    "id": 1,
                    "flagKey": "audit-flag",
                    "name": "Audit Flag",
                    "environment": "DEV",
                    "enabled": true,
                    "rolloutPercentage": 50,
                    "startDate": "2026-10-06T12:30:45.123Z",
                    "endDate": "2026-10-06T18:00:00Z"
                }
                """;
        FlagAuditSnapshot modernSnapshot = objectMapper.readValue(modernSnapshotJson, FlagAuditSnapshot.class);
        assertThat(modernSnapshot.startDate()).isEqualTo(Instant.parse("2026-10-06T12:30:45.123Z"));
        assertThat(modernSnapshot.endDate()).isEqualTo(Instant.parse("2026-10-06T18:00:00Z"));
    }
}
