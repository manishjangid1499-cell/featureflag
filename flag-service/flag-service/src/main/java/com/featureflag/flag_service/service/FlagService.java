package com.featureflag.flag_service.service;

import com.featureflag.flag_service.dto.FlagRequest;
import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.event.FlagEvent;
import com.featureflag.flag_service.kafka.FlagEventProducer;
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

    private static final String ALL_FLAGS_KEY = "all_flags";

    public FeatureFlag createFlag(FlagRequest request) {

        FeatureFlag flag = FeatureFlag.builder()
                .name(request.getName())
                .flagKey(request.getFlagKey())
                .enabled(request.getEnabled())
                .description(request.getDescription())
                .build();

        FeatureFlag savedFlag = repository.save(flag);

        redisTemplate.delete(ALL_FLAGS_KEY);

        producer.publishEvent(
                new FlagEvent(
                        "FLAG_CREATED",
                        savedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        return savedFlag;
    }

    @SuppressWarnings("unchecked")
    public List<FeatureFlag> getAllFlags() {

        List<FeatureFlag> cachedFlags =
                (List<FeatureFlag>) redisTemplate.opsForValue()
                        .get(ALL_FLAGS_KEY);

        if (cachedFlags != null) {

            System.out.println("Fetching flags from Redis");

            return cachedFlags;
        }

        System.out.println("Fetching flags from MySQL");

        List<FeatureFlag> flags = repository.findAll();

        redisTemplate.opsForValue()
                .set(ALL_FLAGS_KEY, flags);

        return flags;
    }

    public FeatureFlag getByKey(String key) {

        return repository.findByFlagKey(key)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));
    }

    public FeatureFlag updateFlag(Long id, FlagRequest request) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setName(request.getName());
        flag.setFlagKey(request.getFlagKey());
        flag.setEnabled(request.getEnabled());
        flag.setDescription(request.getDescription());

        FeatureFlag updatedFlag = repository.save(flag);

        redisTemplate.delete(ALL_FLAGS_KEY);

        producer.publishEvent(
                new FlagEvent(
                        "FLAG_UPDATED",
                        updatedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        return updatedFlag;
    }

    public String deleteFlag(Long id) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        repository.deleteById(id);

        redisTemplate.delete(ALL_FLAGS_KEY);

        producer.publishEvent(
                new FlagEvent(
                        "FLAG_DELETED",
                        flag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        return "Flag Deleted Successfully";
    }

    public FeatureFlag toggleFlag(Long id) {

        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Flag not found"));

        flag.setEnabled(!flag.getEnabled());

        FeatureFlag updatedFlag = repository.save(flag);

        redisTemplate.delete(ALL_FLAGS_KEY);

        producer.publishEvent(
                new FlagEvent(
                        "FLAG_TOGGLED",
                        updatedFlag.getFlagKey(),
                        LocalDateTime.now().toString()
                )
        );

        return updatedFlag;
    }
}