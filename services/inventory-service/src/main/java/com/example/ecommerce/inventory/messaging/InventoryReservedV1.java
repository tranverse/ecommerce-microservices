package com.example.ecommerce.inventory.messaging;

import java.util.UUID;

public record InventoryReservedV1(UUID orderId, UUID reservationId) {
}
