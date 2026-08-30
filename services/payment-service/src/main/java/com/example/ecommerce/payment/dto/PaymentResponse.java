package com.example.ecommerce.payment.dto;

import com.example.ecommerce.payment.domain.PaymentFailureReason;
import com.example.ecommerce.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        PaymentFailureReason failureReason,
        String providerReference,
        String refundReference,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
