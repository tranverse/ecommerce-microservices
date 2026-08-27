package com.example.ecommerce.product.dto;

import com.example.ecommerce.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        ProductStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
