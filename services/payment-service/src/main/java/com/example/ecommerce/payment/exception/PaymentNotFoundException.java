package com.example.ecommerce.payment.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID orderId) {
        super("Payment for order '" + orderId + "' was not found");
    }
}
