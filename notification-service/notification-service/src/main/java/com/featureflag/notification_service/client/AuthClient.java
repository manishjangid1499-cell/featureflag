package com.featureflag.notification_service.client;

import com.featureflag.notification_service.dto.TokenValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "AUTH-SERVICE")
public interface AuthClient {

    @GetMapping("/auth/validate")
    TokenValidationResponse validateToken(
            @RequestParam String token
    );

    @GetMapping("/auth/recipients")
    List<String> getNotificationRecipients(
            @RequestParam(required = false) List<String> roles
    );
}