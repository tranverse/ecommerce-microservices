package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record InventoryReservationFailedV1(
        UUID orderId,
        InventoryReservationFailureReasonV1 reason
) {
}
