package com.example.ecommerce.payment;

import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.dto.ProcessPaymentCommand;
import com.example.ecommerce.payment.exception.InvalidPaymentStateException;
import com.example.ecommerce.payment.exception.PaymentConflictException;
import com.example.ecommerce.payment.exception.PaymentProcessorUnavailableException;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.PaymentProcessor;
import com.example.ecommerce.payment.processor.RefundResult;
import com.example.ecommerce.payment.repository.PaymentRepository;
import com.example.ecommerce.payment.service.PaymentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentWorkflowIntegrationTest {

    @Autowired
    private PaymentApplicationService service;

    @Autowired
    private PaymentRepository repository;

    @MockitoBean
    private PaymentProcessor processor;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        reset(processor);
    }

    @Test
    void processesAndReplaysAnApprovedPaymentIdempotently() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenAnswer(invocation -> ChargeResult.approved(
                "charge-" + invocation.<com.example.ecommerce.payment.processor.ChargeRequest>getArgument(0)
                        .paymentId()
        ));

        PaymentResponse first = service.process(command(orderId, "25.50"));
        PaymentResponse replay = service.process(command(orderId, "25.50"));

        assertThat(first.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(replay.id()).isEqualTo(first.id());
        verify(processor, times(1)).charge(any());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsReusedOrderIdWithDifferentCommercialData() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenReturn(ChargeResult.approved("charge-conflict-test"));
        service.process(command(orderId, "25.50"));

        assertThatThrownBy(() -> service.process(command(orderId, "30.00")))
                .isInstanceOf(PaymentConflictException.class);
        verify(processor, times(1)).charge(any());
    }

    @Test
    void recordsABusinessDeclineAsATerminalOutcome() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenReturn(ChargeResult.declined());

        PaymentResponse declined = service.process(command(orderId, "25.50"));
        PaymentResponse replay = service.process(command(orderId, "25.50"));

        assertThat(declined.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.failureReason()).hasToString("DECLINED");
        assertThat(replay.id()).isEqualTo(declined.id());
        verify(processor, times(1)).charge(any());
    }

    @Test
    void leavesAPaymentPendingAfterTechnicalOutageAndCompletesOnRetry() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any()))
                .thenThrow(new PaymentProcessorUnavailableException())
                .thenReturn(ChargeResult.approved("charge-after-retry"));

        assertThatThrownBy(() -> service.process(command(orderId, "25.50")))
                .isInstanceOf(PaymentProcessorUnavailableException.class);
        assertThat(repository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        PaymentResponse retried = service.process(command(orderId, "25.50"));

        assertThat(retried.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(processor, times(2)).charge(any());
    }

    @Test
    void refundsACompletedPaymentIdempotently() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenReturn(ChargeResult.approved("charge-refund-test"));
        when(processor.refund(any())).thenReturn(new RefundResult("refund-test"));
        service.process(command(orderId, "25.50"));

        PaymentResponse refunded = service.refund(orderId);
        PaymentResponse replay = service.refund(orderId);

        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(replay.id()).isEqualTo(refunded.id());
        verify(processor, times(1)).refund(any());
    }

    @Test
    void leavesACompletedPaymentRefundableAfterProcessorOutage() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenReturn(ChargeResult.approved("charge-refund-retry"));
        when(processor.refund(any()))
                .thenThrow(new PaymentProcessorUnavailableException())
                .thenReturn(new RefundResult("refund-after-retry"));
        service.process(command(orderId, "25.50"));

        assertThatThrownBy(() -> service.refund(orderId))
                .isInstanceOf(PaymentProcessorUnavailableException.class);
        assertThat(repository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);

        PaymentResponse retried = service.refund(orderId);

        assertThat(retried.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(processor, times(2)).refund(any());
    }

    @Test
    void doesNotRefundAPendingOrDeclinedPayment() {
        UUID pendingOrder = UUID.randomUUID();
        when(processor.charge(any())).thenThrow(new PaymentProcessorUnavailableException());
        assertThatThrownBy(() -> service.process(command(pendingOrder, "25.50")))
                .isInstanceOf(PaymentProcessorUnavailableException.class);

        assertThatThrownBy(() -> service.refund(pendingOrder))
                .isInstanceOf(InvalidPaymentStateException.class);
        verify(processor, times(0)).refund(any());
    }

    private ProcessPaymentCommand command(UUID orderId, String amount) {
        return new ProcessPaymentCommand(orderId, new BigDecimal(amount), "USD");
    }
}
