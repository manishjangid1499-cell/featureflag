package com.featureflag.notification_service;

import com.featureflag.notification_service.config.KafkaListenerTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
