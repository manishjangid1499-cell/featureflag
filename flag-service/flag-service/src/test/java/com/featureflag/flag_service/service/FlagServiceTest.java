package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock
    private FeatureFlagRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private FlagService flagService;

    private FeatureFlag testFlag;

    @BeforeEach
    void setUp() {
        testFlag = FeatureFlag.builder()
                .id(1L)
                .name("New Checkout Flow")
                .flagKey("NEW_CHECKOUT")
                .environment("DEV")
                .enabled(true)
                .rolloutPercentage(75)
                .description("Checkout redesign")
                .targetUsers(List.of("user123", "user456"))
                .build();
    }

    @Test
    @DisplayName("CASE 1: Same user + same flag + same environment + 75% rollout - Repeated evaluation returns identical result")
    void testEvaluateFlag_DeterministicForSameUser() {
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        FlagEvaluationResponse firstEval = flagService.evaluateFlag("NEW_CHECKOUT", "user_test_101", "DEV");
        boolean firstResult = firstEval.isEnabled();

        for (int i = 0; i < 50; i++) {
            FlagEvaluationResponse eval = flagService.evaluateFlag("NEW_CHECKOUT", "user_test_101", "DEV");
            assertEquals(firstResult, eval.isEnabled(), "Evaluation result must remain stable and identical on iteration " + i);
        }
    }

    @Test
    @DisplayName("CASE 2: Different users + 75% rollout - Distributed deterministically across rollout percentage")
    void testEvaluateFlag_DistributedUsers() {
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        int enabledCount = 0;
        int totalUsers = 1000;

        for (int i = 0; i < totalUsers; i++) {
            FlagEvaluationResponse eval = flagService.evaluateFlag("NEW_CHECKOUT", "user_dist_" + i, "DEV");
            if (eval.isEnabled()) {
                enabledCount++;
            }
        }

        // Expected to be around 75% (e.g. 700 - 800 out of 1000 users)
        assertTrue(enabledCount > 650 && enabledCount < 850,
                "Enabled count should be approximately 75% of total users, actual: " + enabledCount);
    }

    @Test
    @DisplayName("CASE 3: 0% Rollout - Returns FALSE for non-whitelisted users")
    void testEvaluateFlag_ZeroPercentRollout() {
        testFlag.setRolloutPercentage(0);
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        FlagEvaluationResponse response = flagService.evaluateFlag("NEW_CHECKOUT", "normal_user_999", "DEV");

        assertNotNull(response);
        assertFalse(response.isEnabled());
    }

    @Test
    @DisplayName("CASE 4: 100% Rollout - Returns TRUE for users satisfying schedule/enabled")
    void testEvaluateFlag_HundredPercentRollout() {
        testFlag.setRolloutPercentage(100);
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        FlagEvaluationResponse response = flagService.evaluateFlag("NEW_CHECKOUT", "normal_user_999", "DEV");

        assertNotNull(response);
        assertTrue(response.isEnabled());
    }

    @Test
    @DisplayName("CASE 5: Explicitly whitelisted user - Always receives TRUE regardless of rollout percentage")
    void testEvaluateFlag_TargetedUserWhitelisted() {
        testFlag.setRolloutPercentage(0); // Even at 0% rollout
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        FlagEvaluationResponse response = flagService.evaluateFlag("NEW_CHECKOUT", "user123", "DEV");

        assertNotNull(response);
        assertTrue(response.isEnabled());
        assertTrue(response.isTargetedUser());
    }

    @Test
    @DisplayName("CASE 6: Outside scheduled window - Returns FALSE")
    void testEvaluateFlag_OutOfScheduleWindow() {
        testFlag.setStartDate(LocalDateTime.now().plusDays(2)); // Future start
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "DEV")).thenReturn(Optional.of(testFlag));

        FlagEvaluationResponse response = flagService.evaluateFlag("NEW_CHECKOUT", "user123", "DEV");

        assertNotNull(response);
        assertFalse(response.isEnabled());
        assertFalse(response.isWithinSchedule());
    }

    @Test
    @DisplayName("CASE 7: Wrong Environment - Throws ResourceNotFoundException")
    void testEvaluateFlag_WrongEnvironment() {
        when(repository.findByFlagKeyAndEnvironment("NEW_CHECKOUT", "PROD")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                flagService.evaluateFlag("NEW_CHECKOUT", "user123", "PROD")
        );
    }

    @Test
    @DisplayName("Evaluate Flag - Blank userId is rejected")
    void testEvaluateFlag_BlankUserIdRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.evaluateFlag(
                        "NEW_CHECKOUT",
                        "   ",
                        "DEV"
                )
        );
        verify(
                repository,
                never()
        ).findByFlagKeyAndEnvironment(
                anyString(),
                anyString()
        );
    }
    @Test
    @DisplayName("Evaluate Flag - Environment is normalized")
    void testEvaluateFlag_NormalizesEnvironment() {
        when(
                repository.findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                )
        ).thenReturn(Optional.of(testFlag));
        FlagEvaluationResponse response =
                flagService.evaluateFlag(
                        "NEW_CHECKOUT",
                        "user123",
                        " dev "
                );
        assertNotNull(response);
        verify(repository)
                .findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                );
    }

    @Test
    @DisplayName("Create Flag - Saves flag, invalidates Redis cache, publishes events")
    void testCreateFlag_Success() {
        FlagRequest request = new FlagRequest();
        request.setName("New Checkout Flow");
        request.setFlagKey("NEW_CHECKOUT");
        request.setEnvironment("DEV");
        request.setEnabled(true);
        request.setRolloutPercentage(100);

        when(repository.save(any(FeatureFlag.class))).thenReturn(testFlag);

        FeatureFlag created = flagService.createFlag(request);

        assertNotNull(created);
        assertEquals("NEW_CHECKOUT", created.getFlagKey());
        verify(redisTemplate, times(1)).delete("all_flags");
        verify(outboxService, times(1)).enqueueFlagEvent(anyString(), anyString());
        verify(outboxService, times(1)).enqueueNotificationEvent(anyString(), anyString());
    }

    @Test
    @DisplayName("Create Flag - Environment is normalized")
    void testCreateFlag_NormalizesEnvironment() {
        FlagRequest request =
                new FlagRequest();
        request.setName("New Checkout Flow");
        request.setFlagKey("NEW_CHECKOUT");
        request.setEnvironment(" dev ");
        request.setEnabled(true);
        request.setRolloutPercentage(100);
        when(
                repository.save(any(FeatureFlag.class))
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );
        FeatureFlag created =
                flagService.createFlag(request);
        assertEquals(
                "DEV",
                created.getEnvironment()
        );
        verify(repository).save(
                argThat(
                        flag ->
                                "DEV".equals(
                                        flag.getEnvironment()
                                )
                )
        );
    }    @Test
    @DisplayName("Create Flag - Unsupported environment is rejected")
    void testCreateFlag_UnsupportedEnvironmentRejected() {
        FlagRequest request =
                new FlagRequest();
        request.setName("New Checkout Flow");
        request.setFlagKey("NEW_CHECKOUT");
        request.setEnvironment("LOCAL");
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.createFlag(request)
        );
        verify(
                repository,
                never()
        ).save(any(FeatureFlag.class));
    }
    @Test
    @DisplayName("Create Flag - Invalid schedule is rejected")
    void testCreateFlag_InvalidScheduleRejected() {
        FlagRequest request =
                new FlagRequest();
        request.setName("New Checkout Flow");
        request.setFlagKey("NEW_CHECKOUT");
        request.setEnvironment("DEV");
        request.setStartDate(
                LocalDateTime.of(
                        2026,
                        8,
                        22,
                        10,
                        0
                )
        );
        request.setEndDate(
                LocalDateTime.of(
                        2026,
                        8,
                        21,
                        10,
                        0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.createFlag(request)
        );
        verify(
                repository,
                never()
        ).save(any(FeatureFlag.class));
    }

    @Test
    @DisplayName("Get All Flags - Reads from MySQL and caches to Redis when cache miss")
    void testGetAllFlags_CacheMiss_FetchesFromDb() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("all_flags")).thenReturn(null);
        when(repository.findAll()).thenReturn(List.of(testFlag));

        List<FeatureFlag> flags = flagService.getAllFlags();

        assertNotNull(flags);
        assertEquals(1, flags.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("Get Flag By Key And Environment - Success")
    void testGetByKey_Success() {
        when(
                repository.findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                )
        ).thenReturn(Optional.of(testFlag));
        FeatureFlag found =
                flagService.getByKey(
                        "NEW_CHECKOUT",
                        "DEV"
                );
        assertNotNull(found);
        assertEquals(
                "NEW_CHECKOUT",
                found.getFlagKey()
        );
    }
    @Test
    @DisplayName("Get Flag By Key And Environment - Not found")
    void testGetByKey_NotFound() {
        when(
                repository.findByFlagKeyAndEnvironment(
                        "UNKNOWN_KEY",
                        "DEV"
                )
        ).thenReturn(Optional.empty());
        assertThrows(
                ResourceNotFoundException.class,
                () -> flagService.getByKey(
                        "UNKNOWN_KEY",
                        "DEV"
                )
        );
    }
    @Test
    @DisplayName("Get Flag By Key - Environment is normalized")
    void testGetByKey_NormalizesEnvironment() {
        when(
                repository.findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                )
        ).thenReturn(Optional.of(testFlag));
        FeatureFlag found =
                flagService.getByKey(
                        "NEW_CHECKOUT",
                        " dev "
                );
        assertNotNull(found);
        verify(repository)
                .findByFlagKeyAndEnvironment(
                        "NEW_CHECKOUT",
                        "DEV"
                );
    }
    @Test
    @DisplayName("Get Flag By Key - Unsupported environment is rejected")
    void testGetByKey_UnsupportedEnvironmentRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.getByKey(
                        "NEW_CHECKOUT",
                        "LOCAL"
                )
        );
        verify(
                repository,
                never()
        ).findByFlagKeyAndEnvironment(
                anyString(),
                anyString()
        );
    }

    @Test
    @DisplayName("Update Flag - Success updates properties and invalidates cache")
    void testUpdateFlag_Success() {
        FlagRequest updateRequest = new FlagRequest();
        updateRequest.setName("Updated Checkout");
        updateRequest.setFlagKey("NEW_CHECKOUT");
        updateRequest.setEnvironment("DEV");
        updateRequest.setEnabled(false);
        updateRequest.setRolloutPercentage(50);

        when(repository.findById(1L)).thenReturn(Optional.of(testFlag));
        when(repository.save(any(FeatureFlag.class))).thenAnswer(i -> i.getArgument(0));

        FeatureFlag updated = flagService.updateFlag(1L, updateRequest);

        assertNotNull(updated);
        assertEquals("Updated Checkout", updated.getName());
        assertFalse(updated.getEnabled());
        verify(redisTemplate, times(1)).delete("all_flags");
        verify(outboxService, times(1)).enqueueFlagEvent(anyString(), anyString());
    }

    @Test
    @DisplayName("Update Flag - Environment is normalized")
    void testUpdateFlag_NormalizesEnvironment() {
        FlagRequest request =
                new FlagRequest();
        request.setName("Updated Checkout");
        request.setFlagKey("NEW_CHECKOUT");
        request.setEnvironment(" qa ");
        when(
                repository.findById(1L)
        ).thenReturn(Optional.of(testFlag));
        when(
                repository.save(
                        any(FeatureFlag.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );
        FeatureFlag updated =
                flagService.updateFlag(
                        1L,
                        request
                );
        assertEquals(
                "QA",
                updated.getEnvironment()
        );
    }
    @Test
    @DisplayName("Update Flag - Unsupported environment is rejected")
    void testUpdateFlag_UnsupportedEnvironmentRejected() {
        FlagRequest request =
                new FlagRequest();
        request.setEnvironment("LOCAL");
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.updateFlag(
                        1L,
                        request
                )
        );
        verify(
                repository,
                never()
        ).findById(anyLong());
    }
    @Test
    @DisplayName("Update Flag - Invalid schedule is rejected")
    void testUpdateFlag_InvalidScheduleRejected() {
        FlagRequest request =
                new FlagRequest();
        request.setEnvironment("DEV");
        request.setStartDate(
                LocalDateTime.of(
                        2026,
                        8,
                        22,
                        10,
                        0
                )
        );
        request.setEndDate(
                LocalDateTime.of(
                        2026,
                        8,
                        21,
                        10,
                        0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> flagService.updateFlag(
                        1L,
                        request
                )
        );
        verify(
                repository,
                never()
        ).findById(anyLong());
    }

    @Test
    @DisplayName("Delete Flag - Success deletes record and publishes event")
    void testDeleteFlag_Success() {
        when(repository.findById(1L)).thenReturn(Optional.of(testFlag));

        String result = flagService.deleteFlag(1L);

        assertNotNull(result);
        assertTrue(result.contains("Successfully"));
        verify(repository, times(1)).deleteById(1L);
        verify(redisTemplate, times(1)).delete("all_flags");
        verify(outboxService, times(1)).enqueueFlagEvent(anyString(), anyString());
    }

    @Test
    @DisplayName("Toggle Flag - Switches enabled state")
    void testToggleFlag_Success() {
        testFlag.setEnabled(true);
        when(repository.findById(1L)).thenReturn(Optional.of(testFlag));
        when(repository.save(any(FeatureFlag.class))).thenAnswer(i -> i.getArgument(0));

        FeatureFlag toggled = flagService.toggleFlag(1L);

        assertNotNull(toggled);
        assertFalse(toggled.getEnabled());
        verify(redisTemplate, times(1)).delete("all_flags");
    }
}
