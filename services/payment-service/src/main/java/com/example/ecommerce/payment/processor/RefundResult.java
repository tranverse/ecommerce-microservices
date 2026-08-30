package com.example.ecommerce.payment.processor;

public record RefundResult(String providerReference) {

    public RefundResult {
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("refund must have a provider reference");
        }
    }
}
