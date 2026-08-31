package com.example.ecommerce.notification.messaging;

import java.util.UUID;

public class ConflictingNotificationEventException extends RuntimeException {

    public ConflictingNotificationEventException(UUID orderId) {
        super("Order received contradictory terminal notification events: " + orderId);
    }
}
