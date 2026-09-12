package com.featureflag.notification_service.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaListenerTestConfiguration {

    @Bean
    static BeanPostProcessor disableKafkaListenerAutoStartup() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(
                    Object bean,
                    String beanName
            ) {
                if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
                    factory.setAutoStartup(false);
                }
                return bean;
            }
        };
    }
}
