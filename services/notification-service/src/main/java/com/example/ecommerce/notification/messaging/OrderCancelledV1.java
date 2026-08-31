package com.example.ecommerce.notification.messaging;

import java.util.UUID;

public record OrderCancelledV1(
        UUID orderId,
        UUID customerId,
        OrderCancellationReasonV1 reason
) {
}
