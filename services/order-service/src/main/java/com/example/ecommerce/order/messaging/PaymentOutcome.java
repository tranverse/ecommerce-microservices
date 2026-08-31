package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.UUID;

public sealed interface PaymentOutcome
        permits PaymentCompletedOutcome, PaymentFailedOutcome {

    UUID eventId();

    String eventType();

    Instant occurredAt();

    String correlationId();

    UUID orderId();

    UUID paymentId();
}
