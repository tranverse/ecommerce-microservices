package com.example.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetStockRequest(
        @NotNull @Min(0) Integer totalQuantity
) {
}
