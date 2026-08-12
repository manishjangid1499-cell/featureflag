package com.featureflag.audit_service.client;

import com.featureflag.audit_service.dto.TokenValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "AUTH-SERVICE")
public interface AuthClient {

    @GetMapping("/auth/validate")
    TokenValidationResponse validateToken(@RequestParam("token") String token);
}
