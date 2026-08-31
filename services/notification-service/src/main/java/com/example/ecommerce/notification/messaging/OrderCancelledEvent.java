package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID customerId,
        OrderCancellationReasonV1 reason
) implements OrderLifecycleEvent {

    @Override
    public NotificationType notificationType() {
        return NotificationType.ORDER_CANCELLED;
    }

    @Override
    public String content() {
        return "Your order " + orderId + " was cancelled. Reason: " + reason + ".";
    }

    @Override
    public String cancellationReason() {
        return reason.name();
    }
}
