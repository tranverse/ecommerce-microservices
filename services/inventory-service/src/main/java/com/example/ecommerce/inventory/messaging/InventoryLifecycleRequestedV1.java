package com.example.ecommerce.inventory.messaging;

import java.util.UUID;

public record InventoryLifecycleRequestedV1(UUID orderId) {
}
