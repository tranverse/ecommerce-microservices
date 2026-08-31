package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record PaymentFailedV1(
        UUID orderId,
        UUID paymentId,
        PaymentFailureReasonV1 reason
) {
}
