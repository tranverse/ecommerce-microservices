package com.example.ecommerce.inventory.messaging;

import java.util.UUID;

public record InventoryReservationFailedV1(
        UUID orderId,
        InventoryReservationFailureReason reason
) {
}
