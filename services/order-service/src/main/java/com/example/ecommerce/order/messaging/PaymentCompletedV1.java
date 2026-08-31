package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record PaymentCompletedV1(UUID orderId, UUID paymentId) {
}
