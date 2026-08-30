package com.example.ecommerce.payment.processor;

public interface PaymentProcessor {

    /**
     * Implementations must treat {@link ChargeRequest#paymentId()} as an idempotency key and
     * return the same business outcome/reference when the same charge is delivered again.
     */
    ChargeResult charge(ChargeRequest request);

    /**
     * Implementations must treat {@link RefundRequest#paymentId()} as an idempotency key and
     * return the same refund reference when the same refund is delivered again.
     */
    RefundResult refund(RefundRequest request);
}
