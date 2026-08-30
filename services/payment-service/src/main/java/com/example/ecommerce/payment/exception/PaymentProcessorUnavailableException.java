package com.example.ecommerce.payment.exception;

public class PaymentProcessorUnavailableException extends RuntimeException {

    public PaymentProcessorUnavailableException() {
        super("Payment processor is temporarily unavailable");
    }
}
