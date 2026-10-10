package com.featureflag.flag_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;
import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.exception.InvalidOperationException;
import com.featureflag.flag_service.exception.ResourceNotFoundException;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class FlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxService outboxService;
    private final Clock clock;
    private final boolean evaluationConfigCacheEnabled;

    @Autowired
    public FlagService(
            FeatureFlagRepository repository,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            OutboxService outboxService,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${features.evaluation.config-cache-enabled:false}")
            boolean evaluationConfigCacheEnabled) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.outboxService = outboxService;
        this.clock = clock;
        this.evaluationConfigCacheEnabled = evaluationConfigCacheEnabled;
    }

    public FlagService(
            FeatureFlagRepository repository,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            OutboxService outboxService,
            Clock clock) {
        this(repository, redisTemplate, objectMapper, outboxService, clock, false);
    }

    public FlagService(
            FeatureFlagRepository repository,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            OutboxService outboxService) {
        this(repository, redisTemplate, objectMapper, outboxService, Clock.systemUTC(), false);
    }

    private static final String FLAG_CONFIG_CACHE_PREFIX = "flag:config:";
    private static final Duration FLAG_CONFIG_CACHE_TTL = Duration.ofMinutes(5);
    private static final Set<String> SUPPORTED_ENVIRONMENTS =
            Set.of("DEV", "QA", "STAGING", "PROD");

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

        invalidateFlagConfigCacheAfterCommit(
                savedFlag.getEnvironment(),
                savedFlag.getFlagKey()
        );

        publishFlagEvent(
                "FLAG_CREATED",
                savedFlag.getFlagKey(),
                savedFlag.getEnvironment()
        );

        // The outbox publisher resolves recipients through Auth after this transaction commits.
        publishNotification(
                "Feature Flag Created: " + savedFlag.getFlagKey(),
                "Feature flag '" + savedFlag.getFlagKey()
                        + "' was created for environment " + savedFlag.getEnvironment() + "."
        );

        return savedFlag;
    }



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

    public FeatureFlag getById(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Flag not found with id: " + id
                        )
                );
    }


    @Transactional
    public FeatureFlag updateFlag(
            Long id,
            FlagRequest request
    ) {
        if (request.getEnvironment() != null) {
            normalizeEnvironment(request.getEnvironment());
        }
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

        validateIdentityImmutability(flag, request);

        flag.setName(request.getName());
        // Identity fields (flagKey and environment) are immutable; preserve existing stored identity
        if (request.getEnabled() != null) {
            flag.setEnabled(request.getEnabled());
        }
        flag.setDescription(request.getDescription());
        if (request.getRolloutPercentage() != null) {
            flag.setRolloutPercentage(request.getRolloutPercentage());
        }
        flag.setStartDate(request.getStartDate());
        flag.setEndDate(request.getEndDate());
        if (request.getTargetUsers() != null) {
            flag.setTargetUsers(request.getTargetUsers());
        }

        FeatureFlag updatedFlag = repository.save(flag);

        invalidateFlagConfigCacheAfterCommit(
                updatedFlag.getEnvironment(),
                updatedFlag.getFlagKey()
        );

        publishFlagEvent(
                "FLAG_UPDATED",
                updatedFlag.getFlagKey(),
                updatedFlag.getEnvironment()
        );

        publishNotification(
                "Feature Flag Updated: " + updatedFlag.getFlagKey(),
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' in environment " + updatedFlag.getEnvironment() + " was updated."
        );

        return updatedFlag;
    }


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

        invalidateFlagConfigCacheAfterCommit(
                environment,
                flagKey
        );

        publishFlagEvent(
                "FLAG_DELETED",
                flagKey,
                environment
        );

        publishNotification(
                "Feature Flag Deleted: " + flagKey,
                "Feature flag '" + flagKey
                        + "' (" + environment + ") was deleted successfully."
        );

        return "Flag Deleted Successfully";
    }


    @Transactional
    public FeatureFlag setEnabledState(Long id, Boolean desiredEnabled, Long expectedVersion) {
        if (desiredEnabled == null) {
            throw new InvalidOperationException("enabled is required");
        }
        if (expectedVersion == null) {
            throw new InvalidOperationException("expectedVersion is required");
        }
        if (expectedVersion < 0) {
            throw new InvalidOperationException("expectedVersion cannot be negative");
        }

        FeatureFlag flag =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Flag not found with id: " + id
                                )
                        );

        if (!Objects.equals(flag.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(FeatureFlag.class, id);
        }

        // Setting an already-matching state with the CURRENT version is a no-op:
        // no misleading change event or unnecessary version increment.
        if (Objects.equals(flag.getEnabled(), desiredEnabled)) {
            return flag;
        }

        flag.setEnabled(desiredEnabled);

        FeatureFlag updatedFlag = repository.save(flag);

        invalidateFlagConfigCacheAfterCommit(
                updatedFlag.getEnvironment(),
                updatedFlag.getFlagKey()
        );

        publishFlagEvent(
                "FLAG_TOGGLED",
                updatedFlag.getFlagKey(),
                updatedFlag.getEnvironment()
        );

        String status = Boolean.TRUE.equals(updatedFlag.getEnabled()) ? "ENABLED" : "DISABLED";

        publishNotification(
                "Feature Flag Toggled: " + updatedFlag.getFlagKey(),
                "Feature flag '" + updatedFlag.getFlagKey()
                        + "' in " + updatedFlag.getEnvironment()
                        + " is now " + status + "."
        );

        return updatedFlag;
    }

    @Transactional
    public FeatureFlag toggleFlag(Long id, Boolean desiredEnabled, Long expectedVersion) {
        return setEnabledState(id, desiredEnabled, expectedVersion);
    }


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
                getFlagForEvaluation(
                        flagKey,
                        normalizedEnvironment
                );

        Instant now = clock.instant();

        boolean withinSchedule = true;

        if (flag.getStartDate() != null) {
            withinSchedule = !now.isBefore(flag.getStartDate());
        }

        if (withinSchedule && flag.getEndDate() != null) {
            withinSchedule = !now.isAfter(flag.getEndDate());
        }


        boolean targetedUser =
                flag.getTargetUsers() != null
                        && userId != null
                        && flag.getTargetUsers().contains(userId);


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


    private FeatureFlag getFlagForEvaluation(
            String flagKey,
            String environment
    ) {
        if (!evaluationConfigCacheEnabled) {
            return repository
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
        }

        String cacheKey =
                buildFlagConfigCacheKey(
                        environment,
                        flagKey
                );
        try {
            Object cachedObj =
                    redisTemplate
                            .opsForValue()
                            .get(cacheKey);
            if (cachedObj != null) {
                String cachedJson =
                        cachedObj.toString();
                if (!cachedJson.isBlank()) {
                    FeatureFlag cachedFlag =
                            objectMapper.readValue(
                                    cachedJson,
                                    FeatureFlag.class
                            );
                    if (cachedFlag != null) {
                        log.debug(
                                "Evaluation config cache hit; flagKey={} environment={}",
                                flagKey,
                                environment
                        );
                        return cachedFlag;
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Evaluation config cache read failed; falling back to database; flagKey={} environment={} errorType={}",
                    flagKey,
                    environment,
                    e.getClass().getSimpleName()
            );
        }
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
        try {
            String jsonToCache =
                    objectMapper.writeValueAsString(
                            flag
                    );
            redisTemplate
                    .opsForValue()
                    .set(
                            cacheKey,
                            jsonToCache,
                            FLAG_CONFIG_CACHE_TTL
                    );
        } catch (Exception e) {
            log.warn(
                    "Evaluation config cache write failed; flagKey={} environment={} errorType={}",
                    flagKey,
                    environment,
                    e.getClass().getSimpleName()
            );
        }
        return flag;
    }
    private String buildFlagConfigCacheKey(
            String environment,
            String flagKey
    ) {
        return FLAG_CONFIG_CACHE_PREFIX
                + environment
                + ":"
                + flagKey;
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
            return;
        }
        action.run();
    }

    private void invalidateFlagConfigCacheAfterCommit(
            String environment,
            String flagKey
    ) {
        String cacheKey =
                buildFlagConfigCacheKey(
                        environment,
                        flagKey
                );
        runAfterCommitOrNow(() -> deleteFlagConfigCacheKey(cacheKey));
    }

    private void deleteFlagConfigCacheKey(
            String cacheKey
    ) {
        try {
            redisTemplate.delete(cacheKey);
            log.debug(
                    "Evaluation config cache cleared; cacheKey={}",
                    cacheKey
            );
        } catch (Exception e) {
            log.warn(
                    "Failed to clear evaluation config cache; cacheKey={} errorType={}",
                    cacheKey,
                    e.getClass().getSimpleName()
            );
        }
    }


    private void publishFlagEvent(
            String eventType,
            String flagKey,
            String environment
    ) {
        outboxService.enqueueFlagEvent(
                eventType,
                flagKey,
                environment
        );
    }


    private void publishNotification(
            String subject,
            String message
    ) {
        outboxService.enqueueNotificationEvent(
                subject,
                message
        );
    }


    public static String normalizeEnvironment(String environment) {
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

    public static void validateIdentityImmutability(FeatureFlag existing, FlagRequest request) {
        if (request.getFlagKey() != null && !existing.getFlagKey().equalsIgnoreCase(request.getFlagKey().trim())) {
            throw new InvalidOperationException("Flag key cannot be changed after creation");
        }
        if (request.getEnvironment() != null) {
            String normalizedRequested = normalizeEnvironment(request.getEnvironment());
            String normalizedExisting = normalizeEnvironment(existing.getEnvironment());
            if (!normalizedExisting.equalsIgnoreCase(normalizedRequested)) {
                throw new InvalidOperationException("Flag environment cannot be changed after creation");
            }
        }
    }
    private void validateSchedule(
            Instant startDate,
            Instant endDate
    ) {
        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must not be before startDate"
            );
        }
    }
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