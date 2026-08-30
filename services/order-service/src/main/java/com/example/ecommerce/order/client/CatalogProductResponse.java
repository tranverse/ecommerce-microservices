package com.example.ecommerce.order.client;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProductResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String status
) {
}
