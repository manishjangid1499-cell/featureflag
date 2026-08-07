package com.featureflag.auth_service.client;

import com.featureflag.auth_service.config.FeignConfig;
import com.featureflag.auth_service.dto.FeatureFlagResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "FLAG-SERVICE", configuration = FeignConfig.class)
public interface FlagClient {

    @GetMapping("/flags/{key}")
    FeatureFlagResponse getFlagByKey(
            @PathVariable String key);
}