package com.example.ecommerce.payment.processor;

public record ChargeResult(
        Outcome outcome,
        String providerReference
) {

    public ChargeResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome == Outcome.APPROVED && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("approved charge must have a provider reference");
        }
        if (outcome == Outcome.DECLINED && providerReference != null) {
            throw new IllegalArgumentException("declined charge must not have a provider reference");
        }
    }

    public static ChargeResult approved(String providerReference) {
        return new ChargeResult(Outcome.APPROVED, providerReference);
    }

    public static ChargeResult declined() {
        return new ChargeResult(Outcome.DECLINED, null);
    }

    public enum Outcome {
        APPROVED,
        DECLINED
    }
}
