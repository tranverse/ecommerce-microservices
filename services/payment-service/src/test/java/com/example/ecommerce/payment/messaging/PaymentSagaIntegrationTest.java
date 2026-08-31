package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.TestcontainersConfiguration;
import com.example.ecommerce.payment.domain.PaymentFailureReason;
import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.exception.PaymentConflictException;
import com.example.ecommerce.payment.exception.PaymentProcessorUnavailableException;
import com.example.ecommerce.payment.processor.ChargeRequest;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.PaymentProcessor;
import com.example.ecommerce.payment.repository.OutboxEventRepository;
import com.example.ecommerce.payment.repository.PaymentRepository;
import com.example.ecommerce.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
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
class PaymentSagaIntegrationTest {

    @Autowired
    private PaymentSagaMessageHandler handler;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private PaymentProcessor processor;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        paymentRepository.deleteAll();
        reset(processor);
    }

    @Test
    void atomicallyCompletesPaymentRecordsInboxAndCreatesOneOutcome() {
        UUID orderId = UUID.randomUUID();
        PaymentRequestedMessage message = message(orderId, "25.50");
        when(processor.charge(any())).thenAnswer(invocation -> ChargeResult.approved(
                "charge-" + invocation.<ChargeRequest>getArgument(0).paymentId()));

        assertThat(handler.handle(message)).isEqualTo(PaymentSagaProcessingResult.APPLIED);
        assertThat(handler.handle(message)).isEqualTo(PaymentSagaProcessingResult.ALREADY_PROCESSED);

        assertThat(paymentRepository.findByOrderId(orderId)).get().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getProviderReference()).startsWith("charge-");
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo(PaymentEventFactory.PAYMENT_COMPLETED);
            assertThat(outbox.getTopic()).isEqualTo(PaymentEventFactory.PAYMENT_EVENTS_TOPIC);
            assertThat(outbox.getPayload().path("payload").path("paymentId").asText()).isNotBlank();
        });
        verify(processor, times(1)).charge(any());
    }

    @Test
    void recordsADeclineAsABusinessOutcomeInsteadOfRetryingIt() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenReturn(ChargeResult.declined());

        assertThat(handler.handle(message(orderId, "25.50")))
                .isEqualTo(PaymentSagaProcessingResult.APPLIED);

        assertThat(paymentRepository.findByOrderId(orderId)).get().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.DECLINED);
        });
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo(PaymentEventFactory.PAYMENT_FAILED);
            assertThat(outbox.getPayload().path("payload").path("reason").asText())
                    .isEqualTo(PaymentFailureReasonV1.DECLINED.name());
        });
    }

    @Test
    void leavesPendingWithoutInboxOrOutcomeDuringOutageThenCompletesOnRetry() {
        UUID orderId = UUID.randomUUID();
        PaymentRequestedMessage message = message(orderId, "25.50");
        when(processor.charge(any()))
                .thenThrow(new PaymentProcessorUnavailableException())
                .thenAnswer(invocation -> ChargeResult.approved(
                        "charge-" + invocation.<ChargeRequest>getArgument(0).paymentId()));

        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(PaymentProcessorUnavailableException.class);
        assertThat(paymentRepository.findByOrderId(orderId)).get()
                .extracting(payment -> payment.getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(processedEventRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();

        assertThat(handler.handle(message)).isEqualTo(PaymentSagaProcessingResult.APPLIED);

        assertThat(paymentRepository.findByOrderId(orderId)).get()
                .extracting(payment -> payment.getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        verify(processor, times(2)).charge(any());
    }

    @Test
    void recordsSemanticDuplicateWithoutCallingProviderOrCreatingAnotherOutcome() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenAnswer(invocation -> ChargeResult.approved(
                "charge-" + invocation.<ChargeRequest>getArgument(0).paymentId()));
        handler.handle(message(orderId, "25.50"));

        PaymentSagaProcessingResult result = handler.handle(message(orderId, "25.50"));

        assertThat(result).isEqualTo(PaymentSagaProcessingResult.ALREADY_APPLIED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        verify(processor, times(1)).charge(any());
    }

    @Test
    void rejectsASecondCommandWithDifferentCommercialData() {
        UUID orderId = UUID.randomUUID();
        when(processor.charge(any())).thenAnswer(invocation -> ChargeResult.approved(
                "charge-" + invocation.<ChargeRequest>getArgument(0).paymentId()));
        handler.handle(message(orderId, "25.50"));

        assertThatThrownBy(() -> handler.handle(message(orderId, "30.00")))
                .isInstanceOf(PaymentConflictException.class);

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        verify(processor, times(1)).charge(any());
    }

    private PaymentRequestedMessage message(UUID orderId, String amount) {
        return new PaymentRequestedMessage(
                UUID.randomUUID(),
                PaymentRequestedParser.PAYMENT_REQUESTED,
                Instant.now(),
                "payment-saga-test",
                orderId,
                new BigDecimal(amount),
                "USD"
        );
    }
}
