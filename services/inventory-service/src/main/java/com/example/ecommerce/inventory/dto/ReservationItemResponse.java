package com.example.ecommerce.inventory.dto;

import java.util.UUID;

public record ReservationItemResponse(
        UUID productId,
        int quantity
) {
}
