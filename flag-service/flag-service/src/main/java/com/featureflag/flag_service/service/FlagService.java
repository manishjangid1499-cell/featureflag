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

        // Clear Redis cache
        redisTemplate.delete(ALL_FLAGS_KEY);

        // Audit + Analytics Kafka event
        producer.publishEvent(
                new FlagEvent(
                        "FLAG_CREATED",
                        savedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        // Notification Kafka event
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

        System.out.println("Fetching flags from MySQL");

        return repository.findAll();
    }

    // =========================================================
    // GET FLAG BY KEY
    // =========================================================

    public FeatureFlag getByKey(String key) {

        return repository.findByFlagKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));
    }

    // =========================================================
    // UPDATE FLAG
    // =========================================================

    public FeatureFlag updateFlag(Long id, FlagRequest request) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setName(request.getName());
        flag.setFlagKey(request.getFlagKey());
        flag.setEnabled(request.getEnabled());
        flag.setDescription(request.getDescription());
        flag.setEnvironment(request.getEnvironment());
        flag.setRolloutPercentage(request.getRolloutPercentage());
        flag.setStartDate(request.getStartDate());
        flag.setEndDate(request.getEndDate());
        flag.setTargetUsers(request.getTargetUsers());

        FeatureFlag updatedFlag = repository.save(flag);

        // Clear Redis cache
        redisTemplate.delete(ALL_FLAGS_KEY);

        // Audit + Analytics Kafka event
        producer.publishEvent(
                new FlagEvent(
                        "FLAG_UPDATED",
                        updatedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        // Notification Kafka event
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

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        String flagKey = flag.getFlagKey();

        repository.deleteById(id);

        // Clear Redis cache
        redisTemplate.delete(ALL_FLAGS_KEY);

        // Audit + Analytics Kafka event
        producer.publishEvent(
                new FlagEvent(
                        "FLAG_DELETED",
                        flagKey,
                        LocalDateTime.now().toString()
                )
        );

        // Notification Kafka event
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

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setEnabled(
                !Boolean.TRUE.equals(flag.getEnabled())
        );

        FeatureFlag updatedFlag = repository.save(flag);

        // Clear Redis cache
        redisTemplate.delete(ALL_FLAGS_KEY);

        // Audit + Analytics Kafka event
        producer.publishEvent(
                new FlagEvent(
                        "FLAG_TOGGLED",
                        updatedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        // Notification Kafka event
        String status =
                Boolean.TRUE.equals(updatedFlag.getEnabled())
                        ? "ENABLED"
                        : "DISABLED";

        publishNotification(
                "Feature Flag Toggled",
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' is now " + status + "."
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
                repository.findByFlagKeyAndEnvironment(
                        flagKey,
                        environment
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Flag not found: "
                                        + flagKey
                                        + " in environment "
                                        + environment
                        )
                );

        LocalDateTime now = LocalDateTime.now();

        boolean withinSchedule = true;

        // Start date check
        if (flag.getStartDate() != null) {

            withinSchedule =
                    !now.isBefore(flag.getStartDate());
        }

        // End date check
        if (withinSchedule
                && flag.getEndDate() != null) {

            withinSchedule =
                    !now.isAfter(flag.getEndDate());
        }

        // Target user check
        boolean targetedUser =
                flag.getTargetUsers() != null
                        && flag.getTargetUsers().contains(userId);

        boolean enabled = false;

        if (Boolean.TRUE.equals(flag.getEnabled())
                && withinSchedule) {

            if (targetedUser) {

                enabled = true;

            } else {

                Integer rolloutPercentage =
                        flag.getRolloutPercentage();

                if (rolloutPercentage != null
                        && rolloutPercentage > 0) {

                    int bucket =
                            Math.abs(userId.hashCode()) % 100;

                    enabled =
                            bucket < rolloutPercentage;
                }
            }
        }

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
    // SEND NOTIFICATION EVENT
    // =========================================================

    private void publishNotification(
            String subject,
            String message
    ) {

        NotificationEvent event =
                new NotificationEvent();

        event.setRecipient("test@example.com");
        event.setSubject(subject);
        event.setMessage(message);
        event.setType("EMAIL");

        notificationProducer.publishNotification(event);
    }
}