package com.example.ecommerce.payment.messaging;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestedV1(UUID orderId, BigDecimal amount, String currency) {
}
