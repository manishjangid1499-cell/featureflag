package com.featureflag.flag_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.dto.NotificationEvent;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.kafka.FlagEventProducer;
import com.featureflag.flag_service.kafka.NotificationEventProducer;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final FlagEventProducer producer;
    private final NotificationEventProducer notificationProducer;

    private static final String ALL_FLAGS_KEY = "all_flags";

    // =========================================================
    // CREATE FLAG
    // =========================================================

    public FeatureFlag createFlag(FlagRequest request) {

        FeatureFlag flag = FeatureFlag.builder()
                .name(request.getName())
                .flagKey(request.getFlagKey())
                .enabled(request.getEnabled() != null ? request.getEnabled() : false)
                .description(request.getDescription())
                .environment(request.getEnvironment())
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
                        System.out.println("Fetching flags from Redis");
                        return cachedFlags;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Redis cache read failed, falling back to MySQL: " + e.getMessage());
        }

        System.out.println("Fetching flags from MySQL");
        List<FeatureFlag> flags = repository.findAll();

        try {
            String jsonToCache = objectMapper.writeValueAsString(flags);
            redisTemplate.opsForValue().set(ALL_FLAGS_KEY, jsonToCache);
        } catch (Exception e) {
            System.err.println("Redis cache write failed: " + e.getMessage());
        }

        return flags;
    }


    // =========================================================
    // GET FLAG BY KEY
    // =========================================================

    public FeatureFlag getByKey(String key) {

        return repository.findByFlagKey(key)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Flag not found with key: " + key
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

    public FeatureFlag updateFlag(
            Long id,
            FlagRequest request
    ) {

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
        flag.setEnvironment(request.getEnvironment());
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

        FeatureFlag flag =
                repository
                        .findByFlagKeyAndEnvironment(
                                flagKey,
                                environment
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found: "
                                                + flagKey
                                                + " in environment "
                                                + environment
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
                    int bucket = calculateBucket(userId);
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
            System.out.println("Redis cache cleared: " + ALL_FLAGS_KEY);
        } catch (Exception e) {
            System.err.println("Failed to clear Redis cache: " + e.getMessage());
        }
    }


    // =========================================================
    // KAFKA FLAG EVENT
    // =========================================================

    private void publishFlagEvent(
            String eventType,
            String flagKey
    ) {
        try {
            FlagEvent event = new FlagEvent(
                    eventType,
                    flagKey,
                    LocalDateTime.now().toString()
            );
            producer.publishEvent(event);
        } catch (Exception e) {
            System.err.println("Failed to publish Kafka FlagEvent: " + e.getMessage());
        }
    }


    // =========================================================
    // KAFKA NOTIFICATION EVENT
    // =========================================================

    private void publishNotification(
            String subject,
            String message
    ) {
        try {
            NotificationEvent event = new NotificationEvent();
            event.setRecipient(null); // Dynamic recipient resolution in notification-service
            event.setSubject(subject);
            event.setMessage(message);
            event.setType("EMAIL");

            notificationProducer.publishNotification(event);
        } catch (Exception e) {
            System.err.println("Failed to publish Kafka NotificationEvent: " + e.getMessage());
        }
    }


    // =========================================================
    // ROLLOUT BUCKET
    // =========================================================

    private int calculateBucket(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        return Math.floorMod(userId.hashCode(), 100);
    }
}