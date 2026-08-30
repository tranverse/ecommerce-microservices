package com.example.ecommerce.inventory.messaging;

import java.util.List;
import java.util.UUID;

public record InventoryReservationRequestedV1(
        UUID orderId,
        List<Item> items
) {
    public record Item(UUID productId, Integer quantity) {
    }
}
