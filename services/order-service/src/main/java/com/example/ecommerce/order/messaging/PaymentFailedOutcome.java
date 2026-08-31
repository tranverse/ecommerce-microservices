package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentFailedOutcome(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID paymentId,
        PaymentFailureReasonV1 reason
) implements PaymentOutcome {

    public PaymentFailedOutcome {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
