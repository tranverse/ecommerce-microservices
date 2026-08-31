package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.TestcontainersConfiguration;
import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderFailureReason;
import com.example.ecommerce.order.domain.OrderStatus;
import com.example.ecommerce.order.domain.ProductSnapshot;
import com.example.ecommerce.order.repository.CustomerOrderRepository;
import com.example.ecommerce.order.repository.OutboxEventRepository;
import com.example.ecommerce.order.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentOutcomeIntegrationTest {

    @Autowired
    private OrderSagaTransactionService transactionService;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void atomicallyConfirmsOrderRecordsInboxAndCreatesConfirmationEvents() {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder("payment-outcome-key-001"));
        PaymentCompletedOutcome outcome = completedOutcome(order.getId());

        assertThat(transactionService.handle(outcome)).isEqualTo(OrderSagaProcessingResult.APPLIED);
        assertThat(transactionService.handle(outcome))
                .isEqualTo(OrderSagaProcessingResult.ALREADY_PROCESSED);

        assertThat(orderRepository.findById(order.getId())).get().satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(updated.getFailureReason()).isNull();
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(
                        OrderEventFactory.INVENTORY_CONFIRMATION_REQUESTED,
                        OrderEventFactory.ORDER_CONFIRMED
                );
        assertThat(outboxEventRepository.findAll()).allSatisfy(outbox -> {
            assertThat(outbox.getCorrelationId()).isEqualTo(outcome.correlationId());
            assertThat(outbox.getEventKey()).isEqualTo(order.getId().toString());
        });
    }

    @Test
    void compensatesInventoryAndCancelsOrderWhenPaymentIsDeclined() {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder("payment-outcome-key-002"));
        PaymentFailedOutcome outcome = failedOutcome(order.getId());

        assertThat(transactionService.handle(outcome)).isEqualTo(OrderSagaProcessingResult.APPLIED);

        assertThat(orderRepository.findById(order.getId())).get().satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(updated.getFailureReason()).isEqualTo(OrderFailureReason.PAYMENT_FAILED);
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder(
                        OrderEventFactory.INVENTORY_RELEASE_REQUESTED,
                        OrderEventFactory.ORDER_CANCELLED
                );
        assertThat(outboxEventRepository.findAll())
                .filteredOn(outbox -> outbox.getEventType().equals(OrderEventFactory.ORDER_CANCELLED))
                .singleElement()
                .satisfies(outbox -> assertThat(outbox.getPayload().path("payload").path("reason").asText())
                        .isEqualTo(OrderCancellationReasonV1.PAYMENT_FAILED.name()));
    }

    @Test
    void treatsANewEventIdForAnAlreadyAppliedSuccessAsASemanticDuplicate() {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder("payment-outcome-key-003"));
        transactionService.handle(completedOutcome(order.getId()));

        OrderSagaProcessingResult result = transactionService.handle(completedOutcome(order.getId()));

        assertThat(result).isEqualTo(OrderSagaProcessingResult.ALREADY_APPLIED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    void treatsANewEventIdForAnAlreadyAppliedFailureAsASemanticDuplicate() {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder("payment-outcome-key-004"));
        transactionService.handle(failedOutcome(order.getId()));

        OrderSagaProcessingResult result = transactionService.handle(failedOutcome(order.getId()));

        assertThat(result).isEqualTo(OrderSagaProcessingResult.ALREADY_APPLIED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    void rollsBackInboxAndOutboxWhenPaymentOutcomesConflict() {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder("payment-outcome-key-005"));
        transactionService.handle(failedOutcome(order.getId()));
        long inboxCount = processedEventRepository.count();
        long outboxCount = outboxEventRepository.count();

        assertThatThrownBy(() -> transactionService.handle(completedOutcome(order.getId())))
                .isInstanceOf(ConflictingSagaOutcomeException.class);

        assertThat(orderRepository.findById(order.getId())).get().satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(updated.getFailureReason()).isEqualTo(OrderFailureReason.PAYMENT_FAILED);
        });
        assertThat(processedEventRepository.count()).isEqualTo(inboxCount);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCount);
    }

    private CustomerOrder paymentPendingOrder(String idempotencyKey) {
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(),
                idempotencyKey,
                "e".repeat(64),
                List.of(new ProductSnapshot(
                        UUID.randomUUID(),
                        "SKU-PAYMENT-001",
                        "Payment outcome test product",
                        new BigDecimal("18.00"),
                        "USD",
                        1
                ))
        );
        order.markInventoryReserved();
        order.markPaymentPending();
        return order;
    }

    private PaymentCompletedOutcome completedOutcome(UUID orderId) {
        return new PaymentCompletedOutcome(
                UUID.randomUUID(),
                PaymentOutcomeParser.PAYMENT_COMPLETED,
                Instant.now(),
                "order-payment-outcome-test",
                orderId,
                UUID.randomUUID()
        );
    }

    private PaymentFailedOutcome failedOutcome(UUID orderId) {
        return new PaymentFailedOutcome(
                UUID.randomUUID(),
                PaymentOutcomeParser.PAYMENT_FAILED,
                Instant.now(),
                "order-payment-outcome-test",
                orderId,
                UUID.randomUUID(),
                PaymentFailureReasonV1.DECLINED
        );
    }
}
