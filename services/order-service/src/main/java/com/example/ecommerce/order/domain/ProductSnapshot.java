package com.example.ecommerce.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(
        UUID productId,
        String sku,
        String name,
        BigDecimal unitPrice,
        String currency,
        int quantity
) {
}
