package com.example.ecommerce.payment.processor;

import com.example.ecommerce.payment.exception.PaymentProcessorUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentProcessor implements PaymentProcessor {

    private final SimulatedPaymentProcessorProperties properties;

    public SimulatedPaymentProcessor(SimulatedPaymentProcessorProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        requireAvailable();
        if (properties.declinePayments()) {
            return ChargeResult.declined();
        }
        return ChargeResult.approved("sim-charge-" + request.paymentId());
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requireAvailable();
        return new RefundResult("sim-refund-" + request.paymentId());
    }

    private void requireAvailable() {
        if (properties.unavailable()) {
            throw new PaymentProcessorUnavailableException();
        }
    }
}
