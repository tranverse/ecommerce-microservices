package com.example.ecommerce.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatedNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    private final SimulatedNotificationSenderProperties properties;

    public SimulatedNotificationSender(SimulatedNotificationSenderProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(NotificationDelivery delivery) {
        if (properties.failDeliveries()) {
            throw new NotificationDeliveryException("Simulated notification provider is unavailable");
        }
        log.info("Simulated notification delivery notificationId={} customerId={} type={} channel={}",
                delivery.notificationId(), delivery.customerId(), delivery.type(), delivery.channel());
    }
}
