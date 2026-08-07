package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<FeatureFlag> getAllFlags() {

        String key = "all_flags";

        List<FeatureFlag> cachedFlags =
                (List<FeatureFlag>) redisTemplate.opsForValue().get(key);

        if (cachedFlags != null) {

            System.out.println("Fetching flags from Redis");

            return cachedFlags;
        }

        System.out.println("Fetching flags from MySQL");

        List<FeatureFlag> flags = repository.findAll();

        redisTemplate.opsForValue().set(key, flags);

        return flags;
    }
}