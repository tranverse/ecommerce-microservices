package com.example.ecommerce.payment.processor;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRequest(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency
) {
}
