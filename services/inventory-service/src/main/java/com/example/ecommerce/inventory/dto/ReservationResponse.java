package com.example.ecommerce.inventory.dto;

import com.example.ecommerce.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        ReservationStatus status,
        List<ReservationItemResponse> items,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
