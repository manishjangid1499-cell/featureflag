package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.dto.NotificationEvent;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.kafka.FlagEventProducer;
import com.featureflag.flag_service.kafka.NotificationEventProducer;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
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
                .enabled(request.getEnabled())
                .description(request.getDescription())
                .environment(request.getEnvironment())
                .rolloutPercentage(request.getRolloutPercentage())
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

        // Publish notification event
        publishNotification(
                "Feature Flag Created",
                "Feature flag '" + savedFlag.getFlagKey()
                        + "' was created successfully."
        );

        return savedFlag;
    }


    // =========================================================
    // GET ALL FLAGS
    // =========================================================

    public List<FeatureFlag> getAllFlags() {

        List<FeatureFlag> cachedFlags =
                getCachedFlags();

        if (cachedFlags != null) {

            System.out.println(
                    "Fetching flags from Redis"
            );

            return cachedFlags;
        }

        System.out.println(
                "Fetching flags from MySQL"
        );

        List<FeatureFlag> flags =
                repository.findAll();

        redisTemplate.opsForValue()
                .set(ALL_FLAGS_KEY, flags);

        return flags;
    }


    // =========================================================
    // GET FLAG BY KEY
    // =========================================================

    public FeatureFlag getByKey(String key) {

        return repository.findByFlagKey(key)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Flag not found: " + key
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
                                new RuntimeException(
                                        "Flag not found with id: " + id
                                )
                        );

        flag.setName(request.getName());
        flag.setFlagKey(request.getFlagKey());
        flag.setEnabled(request.getEnabled());
        flag.setDescription(request.getDescription());
        flag.setEnvironment(request.getEnvironment());
        flag.setRolloutPercentage(
                request.getRolloutPercentage()
        );
        flag.setStartDate(request.getStartDate());
        flag.setEndDate(request.getEndDate());
        flag.setTargetUsers(request.getTargetUsers());

        FeatureFlag updatedFlag =
                repository.save(flag);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_UPDATED",
                updatedFlag.getFlagKey()
        );

        // Publish notification
        publishNotification(
                "Feature Flag Updated",
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' was updated successfully."
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
                                new RuntimeException(
                                        "Flag not found with id: " + id
                                )
                        );

        String flagKey =
                flag.getFlagKey();

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
                "Feature Flag Deleted",
                "Feature flag '" + flagKey
                        + "' was deleted successfully."
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
                                new RuntimeException(
                                        "Flag not found with id: " + id
                                )
                        );

        // Null-safe toggle
        flag.setEnabled(
                !Boolean.TRUE.equals(
                        flag.getEnabled()
                )
        );

        FeatureFlag updatedFlag =
                repository.save(flag);

        // Invalidate Redis cache
        clearFlagCache();

        // Publish event for Audit + Analytics
        publishFlagEvent(
                "FLAG_TOGGLED",
                updatedFlag.getFlagKey()
        );

        String status =
                Boolean.TRUE.equals(
                        updatedFlag.getEnabled()
                )
                        ? "ENABLED"
                        : "DISABLED";

        // Publish notification
        publishNotification(
                "Feature Flag Toggled",
                "Feature flag '"
                        + updatedFlag.getFlagKey()
                        + "' is now "
                        + status
                        + "."
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
                                new RuntimeException(
                                        "Flag not found: "
                                                + flagKey
                                                + " in environment "
                                                + environment
                                )
                        );

        LocalDateTime now =
                LocalDateTime.now();

        // -----------------------------------------------------
        // Check schedule
        // -----------------------------------------------------

        boolean withinSchedule = true;

        if (flag.getStartDate() != null) {

            withinSchedule =
                    !now.isBefore(
                            flag.getStartDate()
                    );
        }

        if (withinSchedule
                && flag.getEndDate() != null) {

            withinSchedule =
                    !now.isAfter(
                            flag.getEndDate()
                    );
        }


        // -----------------------------------------------------
        // Check targeted user
        // -----------------------------------------------------

        boolean targetedUser =
                flag.getTargetUsers() != null
                        && userId != null
                        && flag.getTargetUsers()
                        .contains(userId);


        // -----------------------------------------------------
        // Determine final enabled state
        // -----------------------------------------------------

        boolean enabled = false;

        if (Boolean.TRUE.equals(
                flag.getEnabled()
        ) && withinSchedule) {

            // Targeted users always receive the feature
            if (targetedUser) {

                enabled = true;

            } else {

                Integer rolloutPercentage =
                        flag.getRolloutPercentage();

                if (rolloutPercentage != null
                        && rolloutPercentage > 0) {

                    int bucket =
                            calculateBucket(userId);

                    enabled =
                            bucket < rolloutPercentage;
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
                flag.getRolloutPercentage(),
                flag.getStartDate(),
                flag.getEndDate(),
                withinSchedule
        );
    }


    // =========================================================
    // REDIS CACHE
    // =========================================================

    @SuppressWarnings("unchecked")
    private List<FeatureFlag> getCachedFlags() {

        return (List<FeatureFlag>)
                redisTemplate.opsForValue()
                        .get(ALL_FLAGS_KEY);
    }


    private void clearFlagCache() {

        redisTemplate.delete(
                ALL_FLAGS_KEY
        );

        System.out.println(
                "Redis cache cleared: "
                        + ALL_FLAGS_KEY
        );
    }


    // =========================================================
    // KAFKA FLAG EVENT
    // =========================================================
    //
    // Used by:
    //     Audit Service
    //     Analytics Service
    //
    // Topic:
    //     feature-flag-events
    // =========================================================

    private void publishFlagEvent(
            String eventType,
            String flagKey
    ) {

        FlagEvent event =
                new FlagEvent(
                        eventType,
                        flagKey,
                        LocalDateTime.now().toString()
                );

        producer.publishEvent(event);

        System.out.println(
                "Flag Kafka Event Published: "
                        + eventType
                        + " - "
                        + flagKey
        );
    }


    // =========================================================
    // KAFKA NOTIFICATION EVENT
    // =========================================================
    //
    // Used by:
    //     Notification Service
    //
    // Topic:
    //     notification-events
    // =========================================================

    private void publishNotification(
            String subject,
            String message
    ) {

        NotificationEvent event =
                new NotificationEvent();

        event.setRecipient(
                "test@example.com"
        );

        event.setSubject(subject);

        event.setMessage(message);

        event.setType("EMAIL");

        notificationProducer.publishNotification(
                event
        );

        System.out.println(
                "Notification Kafka Event Published: "
                        + subject
        );
    }


    // =========================================================
    // ROLLOUT BUCKET
    // =========================================================

    private int calculateBucket(
            String userId
    ) {

        if (userId == null
                || userId.isBlank()) {

            return 0;
        }

        return Math.floorMod(
                userId.hashCode(),
                100
        );
    }
}