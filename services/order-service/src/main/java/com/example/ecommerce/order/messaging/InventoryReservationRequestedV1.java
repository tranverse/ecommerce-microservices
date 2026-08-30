package com.example.ecommerce.order.messaging;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InventoryReservationRequestedV1(
        UUID orderId,
        List<Item> items
) {
    public InventoryReservationRequestedV1 {
        Objects.requireNonNull(orderId, "orderId must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
    }

    public record Item(UUID productId, int quantity) {
        public Item {
            Objects.requireNonNull(productId, "productId must not be null");
            if (quantity < 1 || quantity > 999) {
                throw new IllegalArgumentException("quantity must be between 1 and 999");
            }
        }
    }
}
