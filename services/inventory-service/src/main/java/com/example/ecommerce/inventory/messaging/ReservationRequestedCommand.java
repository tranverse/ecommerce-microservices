package com.example.ecommerce.inventory.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationRequestedCommand(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        UUID orderId,
        List<InventoryReservationRequestedV1.Item> items
) implements InventoryCommand {
}
