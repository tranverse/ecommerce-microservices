package com.example.ecommerce.notification.service;

import com.example.ecommerce.notification.provider.NotificationDelivery;

public record NotificationDeliveryAttempt(
        NotificationDelivery delivery,
        boolean sendRequired
) {
    public static NotificationDeliveryAttempt send(NotificationDelivery delivery) {
        return new NotificationDeliveryAttempt(delivery, true);
    }

    public static NotificationDeliveryAttempt skip() {
        return new NotificationDeliveryAttempt(null, false);
    }
}
