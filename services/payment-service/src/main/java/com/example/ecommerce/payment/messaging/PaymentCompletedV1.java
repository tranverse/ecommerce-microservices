package com.example.ecommerce.payment.messaging;

import java.util.UUID;

public record PaymentCompletedV1(UUID orderId, UUID paymentId) {
}
