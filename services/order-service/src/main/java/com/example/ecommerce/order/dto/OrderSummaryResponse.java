package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.domain.OrderFailureReason;
import com.example.ecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        OrderStatus status,
        OrderFailureReason failureReason,
        String currency,
        BigDecimal totalAmount,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
