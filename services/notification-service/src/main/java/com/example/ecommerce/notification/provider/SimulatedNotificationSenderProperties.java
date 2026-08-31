package com.example.ecommerce.notification.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.simulator")
public record SimulatedNotificationSenderProperties(boolean failDeliveries) {
}
