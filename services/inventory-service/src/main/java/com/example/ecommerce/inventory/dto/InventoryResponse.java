package com.example.ecommerce.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity,
        long version,
        Instant updatedAt
) {
}
