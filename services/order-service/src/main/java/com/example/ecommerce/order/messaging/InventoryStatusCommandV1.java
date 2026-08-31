package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record InventoryStatusCommandV1(UUID orderId) {
}
