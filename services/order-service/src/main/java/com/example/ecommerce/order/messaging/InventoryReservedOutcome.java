package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedOutcome(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        UUID reservationId
) implements InventoryOutcome {
}
