package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentCompletedOutcome(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID paymentId
) implements PaymentOutcome {

    public PaymentCompletedOutcome {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
    }
}
