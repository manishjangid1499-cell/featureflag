package com.featureflag.notification_service;

import com.featureflag.notification_service.config.KafkaListenerTestConfiguration;
import com.featureflag.notification_service.scheduler.NotificationDeliveryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(KafkaListenerTestConfiguration.class)
@SpringBootTest
class NotificationServiceApplicationTests {

	@Autowired
	private ObjectProvider<NotificationDeliveryScheduler>
			deliverySchedulerProvider;

	@Test
	void contextLoads() {
		org.junit.jupiter.api.Assertions.assertNull(
				deliverySchedulerProvider.getIfAvailable()
		);
	}

}
