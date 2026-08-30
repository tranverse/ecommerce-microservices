package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailedOutcome(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        InventoryReservationFailureReasonV1 reason
) implements InventoryOutcome {
}
