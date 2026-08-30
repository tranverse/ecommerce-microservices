package com.example.ecommerce.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentCommand(
        UUID orderId,
        BigDecimal amount,
        String currency
) {
}
