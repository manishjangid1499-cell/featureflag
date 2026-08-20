package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.featureflag.flag_service.dto.FlagEvaluationResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<FeatureFlag> getAllFlags() {

        String key = "all_flags";

        List<FeatureFlag> cachedFlags =
                (List<FeatureFlag>) redisTemplate.opsForValue().get(key);

        if (cachedFlags != null) {

            log.debug("Feature flags cache hit");

            return cachedFlags;
        }

        log.debug("Feature flags cache miss; loading from database");

        List<FeatureFlag> flags = repository.findAll();

        redisTemplate.opsForValue().set(key, flags);

        return flags;
    }
}