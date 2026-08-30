package com.example.ecommerce.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void createsAPendingPaymentWithNormalizedCommercialData() {
        UUID orderId = UUID.randomUUID();

        Payment payment = Payment.create(orderId, new BigDecimal("10"), "usd");

        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getAmount()).isEqualByComparingTo("10.00");
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.matches(new BigDecimal("10.0"), "USD")).isTrue();
    }

    @Test
    void rejectsInvalidAmountAndCurrency() {
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), new BigDecimal("1.001"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(
                UUID.randomUUID(), new BigDecimal("100000000000000000"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), BigDecimal.ONE, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completesIdempotentlyOnlyForTheSameProviderReference() {
        Payment payment = payment();

        assertThat(payment.complete("charge-001")).isTrue();
        assertThat(payment.complete("charge-001")).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThatThrownBy(() -> payment.complete("charge-002"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsABusinessDeclineIdempotently() {
        Payment payment = payment();

        assertThat(payment.fail(PaymentFailureReason.DECLINED)).isTrue();
        assertThat(payment.fail(PaymentFailureReason.DECLINED)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.DECLINED);
        assertThatThrownBy(() -> payment.complete("charge-001"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundsACompletedPaymentIdempotently() {
        Payment payment = payment();
        payment.complete("charge-001");

        assertThat(payment.refund("refund-001")).isTrue();
        assertThat(payment.refund("refund-001")).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReference()).isEqualTo("refund-001");
    }

    @Test
    void rejectsRefundBeforeCompletionAndUnsafeReferences() {
        Payment payment = payment();

        assertThatThrownBy(() -> payment.refund("refund-001"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.complete("unsafe reference"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Payment payment() {
        return Payment.create(UUID.randomUUID(), new BigDecimal("25.50"), "USD");
    }
}
