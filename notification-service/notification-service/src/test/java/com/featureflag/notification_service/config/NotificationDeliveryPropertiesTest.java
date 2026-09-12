package com.featureflag.notification_service.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDeliveryPropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void defaultsAreSafeAndValid() {
        NotificationDeliveryProperties properties =
                new NotificationDeliveryProperties();

        assertTrue(properties.isEnabled());
        assertEquals(5, properties.getMaxAttempts());
        assertEquals(
                Duration.ofSeconds(30),
                properties.getInitialDelay()
        );
        assertEquals(2, properties.getMultiplier());
        assertEquals(
                Duration.ofMinutes(15),
                properties.getMaxDelay()
        );
        assertEquals(25, properties.getBatchSize());
        assertEquals(
                Duration.ofMinutes(2),
                properties.getLeaseDuration()
        );
        assertTrue(
                properties.getLeaseDuration().compareTo(
                        Duration.ofSeconds(30)
                ) > 0
        );
        assertEquals(
                Duration.ofSeconds(5),
                properties.getPollInterval()
        );
        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void rejectsNonPositiveNumericValues() {
        NotificationDeliveryProperties properties =
                new NotificationDeliveryProperties();
        properties.setMaxAttempts(0);
        properties.setMultiplier(0);
        properties.setBatchSize(0);

        assertEquals(3, validator.validate(properties).size());
    }

    @Test
    void rejectsInvalidDurationConfiguration() {
        NotificationDeliveryProperties properties =
                new NotificationDeliveryProperties();
        properties.setInitialDelay(Duration.ofMinutes(2));
        properties.setMaxDelay(Duration.ofMinutes(1));
        properties.setLeaseDuration(Duration.ZERO);

        assertFalse(properties.isDurationConfigurationValid());
        assertEquals(1, validator.validate(properties).size());
    }
}
