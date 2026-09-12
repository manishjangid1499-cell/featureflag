package com.featureflag.notification_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationDeliveryProperties.class)
public class NotificationDeliveryConfiguration {
}
