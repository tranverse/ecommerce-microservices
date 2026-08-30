package com.example.ecommerce.payment.exception;

public class PaymentProcessorContractException extends RuntimeException {

    public PaymentProcessorContractException() {
        super("Payment processor returned an outcome that conflicts with persisted payment state");
    }
}
