package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record InventoryReservedV1(UUID orderId, UUID reservationId) {
}
