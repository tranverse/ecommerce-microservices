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
class OrderSagaIntegrationTest {

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
    void atomicallyAdvancesOrderRecordsInboxAndCreatesOnePaymentCommand() {
        CustomerOrder order = orderRepository.saveAndFlush(order("order-saga-key-001"));
        InventoryReservedOutcome outcome = reservedOutcome(order.getId());

        assertThat(transactionService.handle(outcome)).isEqualTo(OrderSagaProcessingResult.APPLIED);
        assertThat(transactionService.handle(outcome))
                .isEqualTo(OrderSagaProcessingResult.ALREADY_PROCESSED);

        assertThat(orderRepository.findById(order.getId())).get().satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(updated.getFailureReason()).isNull();
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo(OrderEventFactory.PAYMENT_REQUESTED);
            assertThat(outbox.getTopic()).isEqualTo(OrderEventFactory.PAYMENT_COMMANDS_TOPIC);
            assertThat(outbox.getCorrelationId()).isEqualTo(outcome.correlationId());
            assertThat(outbox.getPayload().path("payload").path("amount").decimalValue())
                    .isEqualByComparingTo("24.00");
            assertThat(outbox.getPayload().path("payload").path("currency").asText())
                    .isEqualTo("USD");
        });
    }

    @Test
    void treatsANewEventIdForAnAlreadyAppliedSuccessAsASemanticDuplicate() {
        CustomerOrder order = orderRepository.saveAndFlush(order("order-saga-key-002"));
        transactionService.handle(reservedOutcome(order.getId()));

        OrderSagaProcessingResult result = transactionService.handle(reservedOutcome(order.getId()));

        assertThat(result).isEqualTo(OrderSagaProcessingResult.ALREADY_APPLIED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelsOrderAndPublishesOutcomeWhenInventoryIsInsufficient() {
        CustomerOrder order = orderRepository.saveAndFlush(order("order-saga-key-003"));
        InventoryReservationFailedOutcome outcome = failedOutcome(
                order.getId(), InventoryReservationFailureReasonV1.INSUFFICIENT_INVENTORY);

        assertThat(transactionService.handle(outcome)).isEqualTo(OrderSagaProcessingResult.APPLIED);

        assertThat(orderRepository.findById(order.getId())).get().satisfies(updated -> {
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(updated.getFailureReason()).isEqualTo(OrderFailureReason.INSUFFICIENT_INVENTORY);
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo(OrderEventFactory.ORDER_CANCELLED);
            assertThat(outbox.getTopic()).isEqualTo(OrderEventFactory.ORDER_EVENTS_TOPIC);
            assertThat(outbox.getPayload().path("payload").path("reason").asText())
                    .isEqualTo(OrderCancellationReasonV1.INSUFFICIENT_INVENTORY.name());
        });
    }

    @Test
    void mapsMissingInventoryItemToAnUnavailableOrderCancellation() {
        CustomerOrder order = orderRepository.saveAndFlush(order("order-saga-key-004"));

        transactionService.handle(failedOutcome(
                order.getId(), InventoryReservationFailureReasonV1.ITEM_NOT_FOUND));

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting(CustomerOrder::getFailureReason)
                .isEqualTo(OrderFailureReason.INVENTORY_UNAVAILABLE);
    }

    @Test
    void rollsBackInboxAndOutboxWhenALateFailureConflictsWithPaymentPending() {
        CustomerOrder order = orderRepository.saveAndFlush(order("order-saga-key-005"));
        transactionService.handle(reservedOutcome(order.getId()));
        long inboxCount = processedEventRepository.count();
        long outboxCount = outboxEventRepository.count();

        assertThatThrownBy(() -> transactionService.handle(failedOutcome(
                order.getId(), InventoryReservationFailureReasonV1.REQUEST_CONFLICT)))
                .isInstanceOf(ConflictingSagaOutcomeException.class);

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(processedEventRepository.count()).isEqualTo(inboxCount);
        assertThat(outboxEventRepository.count()).isEqualTo(outboxCount);
    }

    private CustomerOrder order(String idempotencyKey) {
        return CustomerOrder.create(
                UUID.randomUUID(),
                idempotencyKey,
                "c".repeat(64),
                List.of(new ProductSnapshot(
                        UUID.randomUUID(),
                        "SKU-SAGA-001",
                        "Saga test product",
                        new BigDecimal("12.00"),
                        "USD",
                        2
                ))
        );
    }

    private InventoryReservedOutcome reservedOutcome(UUID orderId) {
        return new InventoryReservedOutcome(
                UUID.randomUUID(),
                InventoryOutcomeParser.INVENTORY_RESERVED,
                Instant.now(),
                "order-saga-test",
                orderId,
                UUID.randomUUID()
        );
    }

    private InventoryReservationFailedOutcome failedOutcome(
            UUID orderId,
            InventoryReservationFailureReasonV1 reason
    ) {
        return new InventoryReservationFailedOutcome(
                UUID.randomUUID(),
                InventoryOutcomeParser.INVENTORY_RESERVATION_FAILED,
                Instant.now(),
                "order-saga-test",
                orderId,
                reason
        );
    }
}
