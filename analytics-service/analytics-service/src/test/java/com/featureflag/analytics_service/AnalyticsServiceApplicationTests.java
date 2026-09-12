package com.featureflag.analytics_service;

import com.featureflag.analytics_service.config.KafkaListenerTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest
class AnalyticsServiceApplicationTests {

	@Autowired
	private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

	@Test
	void contextLoads() {
		assertTrue(
				kafkaListenerEndpointRegistry.getListenerContainers()
						.stream()
						.noneMatch(container -> container.isRunning())
		);
	}

}
