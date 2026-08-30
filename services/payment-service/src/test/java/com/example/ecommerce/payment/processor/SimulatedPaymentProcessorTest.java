package com.example.ecommerce.payment.processor;

import com.example.ecommerce.payment.exception.PaymentProcessorUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedPaymentProcessorTest {

    @Test
    void returnsStableReferencesForChargeAndRefundRetries() {
        UUID paymentId = UUID.randomUUID();
        SimulatedPaymentProcessor processor = processor(false, false);
        ChargeRequest charge = charge(paymentId);

        assertThat(processor.charge(charge)).isEqualTo(processor.charge(charge));
        assertThat(processor.refund(refund(paymentId))).isEqualTo(processor.refund(refund(paymentId)));
    }

    @Test
    void supportsAnExplicitDeclineMode() {
        ChargeResult result = processor(true, false).charge(charge(UUID.randomUUID()));

        assertThat(result.outcome()).isEqualTo(ChargeResult.Outcome.DECLINED);
        assertThat(result.providerReference()).isNull();
    }

    @Test
    void mapsConfiguredOutageToATransientException() {
        SimulatedPaymentProcessor processor = processor(false, true);

        assertThatThrownBy(() -> processor.charge(charge(UUID.randomUUID())))
                .isInstanceOf(PaymentProcessorUnavailableException.class);
    }

    private SimulatedPaymentProcessor processor(boolean decline, boolean unavailable) {
        return new SimulatedPaymentProcessor(new SimulatedPaymentProcessorProperties(decline, unavailable));
    }

    private ChargeRequest charge(UUID paymentId) {
        return new ChargeRequest(paymentId, UUID.randomUUID(), new BigDecimal("25.50"), "USD");
    }

    private RefundRequest refund(UUID paymentId) {
        return new RefundRequest(
                paymentId,
                UUID.randomUUID(),
                new BigDecimal("25.50"),
                "USD",
                "sim-charge-" + paymentId
        );
    }
}
