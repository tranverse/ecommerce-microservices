package com.example.ecommerce.payment.exception;

import com.example.ecommerce.payment.domain.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(PaymentStatus status, String operation) {
        super("Cannot " + operation + " payment in status " + status);
    }
}
