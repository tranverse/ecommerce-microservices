package com.example.ecommerce.inventory.messaging;

import java.time.Instant;
import java.util.UUID;

public record ReleaseRequestedCommand(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId
) implements InventoryCommand {
}
