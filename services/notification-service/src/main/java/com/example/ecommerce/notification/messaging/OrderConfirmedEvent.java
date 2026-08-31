package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId
) implements OrderLifecycleEvent {

    @Override
    public NotificationType notificationType() {
        return NotificationType.ORDER_CONFIRMED;
    }

    @Override
    public String content() {
        return "Your order " + orderId + " has been confirmed.";
    }
}
