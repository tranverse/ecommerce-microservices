package com.example.ecommerce.payment.exception;

import java.util.UUID;

public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException(UUID orderId) {
        super("Payment for order '" + orderId + "' already exists with different commercial data");
    }
}
