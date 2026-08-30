package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record OrderCancelledV1(
        UUID orderId,
        UUID customerId,
        OrderCancellationReasonV1 reason
) {
}
