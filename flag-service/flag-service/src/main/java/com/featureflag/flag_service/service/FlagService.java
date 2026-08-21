package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;

    private static final String ALL_FLAGS_KEY = "all_flags";
    private static final Set<String> SUPPORTED_ENVIRONMENTS =
            Set.of("DEV", "QA", "STAGING", "PROD");

    // =========================================================
    // CREATE FLAG
    // =========================================================

    @Transactional
    public FeatureFlag createFlag(FlagRequest request) {
        String environment =
                normalizeEnvironment(request.getEnvironment());
        validateSchedule(
                request.getStartDate(),
                request.getEndDate()
        );

        FeatureFlag flag = FeatureFlag.builder()
                .name(request.getName())
                .flagKey(request.getFlagKey())
                .enabled(request.getEnabled() != null ? request.getEnabled() : false)
                .description(request.getDescription())
                .environment(environment)
                .rolloutPercentage(request.getRolloutPercentage() != null ? request.getRolloutPercentage() : 0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetUsers(request.getTargetUsers())
                .build();

        FeatureFlag savedFlag = repository.save(flag);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_CREATED",
                savedFlag.getFlagKey()
        );

        // Publish notification event (recipients resolved dynamically by notification-service)
        publishNotification(
                "Feature Flag Created: " + savedFlag.getFlagKey(),
                "Feature flag '" + savedFlag.getFlagKey()
                        + "' was created for environment " + savedFlag.getEnvironment() + "."
        );

        return savedFlag;
    }


    // =========================================================
    // GET ALL FLAGS (WITH REDIS CACHING)
    // =========================================================

    public List<FeatureFlag> getAllFlags() {

        try {
            Object cachedObj = redisTemplate.opsForValue().get(ALL_FLAGS_KEY);
            if (cachedObj != null) {
                String cachedJson = cachedObj.toString();
                if (!cachedJson.isBlank()) {
                    List<FeatureFlag> cachedFlags = objectMapper.readValue(
                            cachedJson,
                            new TypeReference<List<FeatureFlag>>() {}
                    );
                    if (cachedFlags != null && !cachedFlags.isEmpty()) {
                        log.debug("Feature flags cache hit");
                        return cachedFlags;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed; falling back to database; errorType={}", e.getClass().getSimpleName());
        }

        log.debug("Feature flags cache miss; loading from database");
        List<FeatureFlag> flags = repository.findAll();

        try {
            String jsonToCache = objectMapper.writeValueAsString(flags);
            redisTemplate.opsForValue().set(ALL_FLAGS_KEY, jsonToCache);
        } catch (Exception e) {
            log.warn("Redis cache write failed; errorType={}", e.getClass().getSimpleName());
        }

        return flags;
    }


    // =========================================================
    // GET FLAG BY KEY
    // =========================================================

    public FeatureFlag getByKey(
            String key,
            String environment
    ) {
        String normalizedEnvironment =
                normalizeEnvironment(environment);
        return repository
                .findByFlagKeyAndEnvironment(
                        key,
                        normalizedEnvironment
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Flag not found with key: "
                                        + key
                                        + " in environment: "
                                        + normalizedEnvironment
                        )
                );
    }

    // =========================================================
    // GET FLAG BY ID
    // =========================================================

    public FeatureFlag getById(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Flag not found with id: " + id
                        )
                );
    }


    // =========================================================
    // UPDATE FLAG
    // =========================================================

    @Transactional
    public FeatureFlag updateFlag(
            Long id,
            FlagRequest request
    ) {
        String environment =
                normalizeEnvironment(request.getEnvironment());
        validateSchedule(
                request.getStartDate(),
                request.getEndDate()
        );

        FeatureFlag flag =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found with id: " + id
                                 )
                        );

        flag.setName(request.getName());
        flag.setFlagKey(request.getFlagKey());
        if (request.getEnabled() != null) {
            flag.setEnabled(request.getEnabled());
        }
        flag.setDescription(request.getDescription());
        flag.setEnvironment(environment);
        if (request.getRolloutPercentage() != null) {
            flag.setRolloutPercentage(request.getRolloutPercentage());
        }
        flag.setStartDate(request.getStartDate());
        flag.setEndDate(request.getEndDate());
        if (request.getTargetUsers() != null) {
            flag.setTargetUsers(request.getTargetUsers());
        }

        FeatureFlag updatedFlag = repository.save(flag);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_UPDATED",
                updatedFlag.getFlagKey()
        );

        // Publish notification
        publishNotification(
                "Feature Flag Updated: " + updatedFlag.getFlagKey(),
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' in environment " + updatedFlag.getEnvironment() + " was updated."
        );

        return updatedFlag;
    }


    // =========================================================
    // DELETE FLAG
    // =========================================================

    @Transactional
    public String deleteFlag(Long id) {

        FeatureFlag flag =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found with id: " + id
                                )
                        );

        String flagKey = flag.getFlagKey();
        String environment = flag.getEnvironment();

        repository.deleteById(id);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_DELETED",
                flagKey
        );

        // Publish notification
        publishNotification(
                "Feature Flag Deleted: " + flagKey,
                "Feature flag '" + flagKey
                        + "' (" + environment + ") was deleted successfully."
        );

        return "Flag Deleted Successfully";
    }


    // =========================================================
    // TOGGLE FLAG
    // =========================================================

    @Transactional
    public FeatureFlag toggleFlag(Long id) {

        FeatureFlag flag =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found with id: " + id
                                )
                        );

        // Null-safe toggle
        flag.setEnabled(!Boolean.TRUE.equals(flag.getEnabled()));

        FeatureFlag updatedFlag = repository.save(flag);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_TOGGLED",
                updatedFlag.getFlagKey()
        );

        String status = Boolean.TRUE.equals(updatedFlag.getEnabled()) ? "ENABLED" : "DISABLED";

        // Publish notification
        publishNotification(
                "Feature Flag Toggled: " + updatedFlag.getFlagKey(),
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' in " + updatedFlag.getEnvironment()
                        + " is now " + status + "."
        );

        return updatedFlag;
    }


    // =========================================================
    // EVALUATE FLAG
    // =========================================================

    public FlagEvaluationResponse evaluateFlag(
            String flagKey,
            String userId,
            String environment
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "userId must not be blank"
            );
        }
        String normalizedEnvironment =
                normalizeEnvironment(environment);

        FeatureFlag flag =
                repository
                        .findByFlagKeyAndEnvironment(
                                flagKey,
                                normalizedEnvironment
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found: "
                                                + flagKey
                                                + " in environment "
                                                + normalizedEnvironment
                                )
                        );

        LocalDateTime now = LocalDateTime.now();

        // -----------------------------------------------------
        // Check schedule
        // -----------------------------------------------------

        boolean withinSchedule = true;

        if (flag.getStartDate() != null) {
            withinSchedule = !now.isBefore(flag.getStartDate());
        }

        if (withinSchedule && flag.getEndDate() != null) {
            withinSchedule = !now.isAfter(flag.getEndDate());
        }


        // -----------------------------------------------------
        // Check targeted user
        // -----------------------------------------------------

        boolean targetedUser =
                flag.getTargetUsers() != null
                        && userId != null
                        && flag.getTargetUsers().contains(userId);


        // -----------------------------------------------------
        // Determine final enabled state
        // -----------------------------------------------------

        boolean enabled = false;

        if (Boolean.TRUE.equals(flag.getEnabled()) && withinSchedule) {

            // Targeted users always receive the feature
            if (targetedUser) {
                enabled = true;
            } else {
                Integer rolloutPercentage = flag.getRolloutPercentage();

                if (rolloutPercentage != null && rolloutPercentage > 0) {
                    int bucket = calculateBucket(flag.getEnvironment(), flag.getFlagKey(), userId);
                    enabled = bucket < rolloutPercentage;
                }
            }
        }


        // -----------------------------------------------------
        // Return evaluation result
        // -----------------------------------------------------

        return new FlagEvaluationResponse(
                flag.getFlagKey(),
                flag.getEnvironment(),
                enabled,
                targetedUser,
                flag.getRolloutPercentage() != null ? flag.getRolloutPercentage() : 0,
                flag.getStartDate(),
                flag.getEndDate(),
                withinSchedule
        );
    }


    // =========================================================
    // REDIS CACHE HELPERS
    // =========================================================

    private void clearFlagCache() {
        try {
            redisTemplate.delete(ALL_FLAGS_KEY);
            log.debug("Feature flags cache cleared");
        } catch (Exception e) {
            log.warn("Failed to clear feature flags cache; errorType={}", e.getClass().getSimpleName());
        }
    }


    // =========================================================
    // KAFKA FLAG EVENT
    // =========================================================

    private void publishFlagEvent(
            String eventType,
            String flagKey
    ) {
        outboxService.enqueueFlagEvent(
                eventType,
                flagKey
        );
    }


    // =========================================================
    // KAFKA NOTIFICATION EVENT
    // =========================================================

    private void publishNotification(
            String subject,
            String message
    ) {
        outboxService.enqueueNotificationEvent(
                subject,
                message
        );
    }


    // =========================================================
    // ENVIRONMENT VALIDATION
    // =========================================================
    private String normalizeEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalArgumentException(
                    "environment must not be blank"
            );
        }
        String normalized =
                environment
                        .trim()
                        .toUpperCase(Locale.ROOT);
        if (!SUPPORTED_ENVIRONMENTS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported environment: "
                            + environment
            );
        }
        return normalized;
    }
    // =========================================================
    // SCHEDULE VALIDATION
    // =========================================================
    private void validateSchedule(
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must not be before startDate"
            );
        }
    }
    // =========================================================
    // ROLLOUT BUCKET
    // =========================================================

    private int calculateBucket(String environment, String flagKey, String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        String input = (environment != null ? environment : "") + ":" + (flagKey != null ? flagKey : "") + ":" + userId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xFF) << 24) |
                        ((hash[1] & 0xFF) << 16) |
                        ((hash[2] & 0xFF) << 8)  |
                        (hash[3] & 0xFF);
            return Math.abs(value % 100);
        } catch (NoSuchAlgorithmException e) {
            return Math.floorMod(input.hashCode(), 100);
        }
    }
}