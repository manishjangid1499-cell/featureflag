package com.featureflag.notification_service.client;

import com.featureflag.notification_service.config.AuthRecipientsFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "AUTH-SERVICE",
        contextId = "authRecipientsClient",
        configuration = AuthRecipientsFeignConfig.class
)
public interface AuthRecipientsClient {

    @GetMapping("/auth/recipients")
    List<String> getNotificationRecipients(
            @RequestParam(required = false) List<String> roles
    );
}
