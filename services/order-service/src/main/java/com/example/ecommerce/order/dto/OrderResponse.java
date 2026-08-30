package com.example.ecommerce.order.dto;

import com.example.ecommerce.order.domain.OrderFailureReason;
import com.example.ecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        OrderFailureReason failureReason,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public OrderResponse {
        items = List.copyOf(items);
    }
}
