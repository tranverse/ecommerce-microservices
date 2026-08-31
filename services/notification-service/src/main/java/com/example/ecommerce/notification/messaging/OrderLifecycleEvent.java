package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public sealed interface OrderLifecycleEvent
        permits OrderConfirmedEvent, OrderCancelledEvent {

    UUID eventId();

    String eventType();

    Instant occurredAt();

    String correlationId();

    UUID orderId();

    UUID customerId();

    NotificationType notificationType();

    String content();

    default String cancellationReason() {
        return null;
    }
}
