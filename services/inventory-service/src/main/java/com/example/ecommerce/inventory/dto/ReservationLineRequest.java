package com.example.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReservationLineRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity
) {
}
