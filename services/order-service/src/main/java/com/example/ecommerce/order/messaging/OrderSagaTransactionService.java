package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderFailureReason;
import com.example.ecommerce.order.domain.OrderStatus;
import com.example.ecommerce.order.repository.CustomerOrderRepository;
import com.example.ecommerce.order.repository.OutboxEventRepository;
import com.example.ecommerce.order.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OrderSagaTransactionService {

    public static final String CONSUMER_NAME = "order-inventory-outcome-v1";

    private static final Logger log = LoggerFactory.getLogger(OrderSagaTransactionService.class);

    private final CustomerOrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventFactory eventFactory;

    public OrderSagaTransactionService(
            CustomerOrderRepository orderRepository,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            OrderEventFactory eventFactory
    ) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventFactory = eventFactory;
    }

    @Transactional
    public OrderSagaProcessingResult handle(InventoryOutcome outcome) {
        CustomerOrder order = orderRepository.findByIdForUpdate(outcome.orderId())
                .orElseThrow(() -> new SagaOrderNotFoundException(outcome.orderId()));

        if (processedEventRepository.existsById(outcome.eventId())) {
            log.info("Ignored duplicate inventory outcome eventId={} eventType={} orderId={}",
                    outcome.eventId(), outcome.eventType(), outcome.orderId());
            return OrderSagaProcessingResult.ALREADY_PROCESSED;
        }

        OrderSagaProcessingResult result = switch (outcome) {
            case InventoryReservedOutcome reserved -> inventoryReserved(order, reserved);
            case InventoryReservationFailedOutcome failed -> inventoryReservationFailed(order, failed);
        };
        processedEventRepository.save(ProcessedEvent.create(outcome, CONSUMER_NAME, Instant.now()));
        return result;
    }

    private OrderSagaProcessingResult inventoryReserved(
            CustomerOrder order,
            InventoryReservedOutcome outcome
    ) {
        OrderStatus currentStatus = order.getStatus();
        switch (currentStatus) {
            case PENDING -> {
                order.markInventoryReserved();
                order.markPaymentPending();
            }
            case INVENTORY_RESERVED -> order.markPaymentPending();
            case PAYMENT_PENDING, CONFIRMED -> {
                log.info("Ignored already-applied inventory success eventId={} orderId={} status={}",
                        outcome.eventId(), outcome.orderId(), currentStatus);
                return OrderSagaProcessingResult.ALREADY_APPLIED;
            }
            case CANCELLED -> throw new ConflictingSagaOutcomeException(
                    outcome.orderId(), currentStatus, outcome.eventType());
        }

        outboxEventRepository.save(eventFactory.paymentRequested(order, outcome.correlationId()));
        log.info("Advanced order to payment pending eventId={} orderId={} reservationId={}",
                outcome.eventId(), outcome.orderId(), outcome.reservationId());
        return OrderSagaProcessingResult.APPLIED;
    }

    private OrderSagaProcessingResult inventoryReservationFailed(
            CustomerOrder order,
            InventoryReservationFailedOutcome outcome
    ) {
        OrderFailureReason failureReason = mapFailureReason(outcome.reason());
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED) {
            if (order.getFailureReason() != failureReason) {
                throw new ConflictingSagaOutcomeException(
                        outcome.orderId(), currentStatus, outcome.eventType());
            }
            log.info("Ignored already-applied inventory failure eventId={} orderId={} reason={}",
                    outcome.eventId(), outcome.orderId(), outcome.reason());
            return OrderSagaProcessingResult.ALREADY_APPLIED;
        }
        if (currentStatus != OrderStatus.PENDING) {
            throw new ConflictingSagaOutcomeException(
                    outcome.orderId(), currentStatus, outcome.eventType());
        }

        order.cancel(failureReason);
        outboxEventRepository.save(eventFactory.orderCancelled(order, outcome.correlationId()));
        log.info("Cancelled order after inventory failure eventId={} orderId={} reason={}",
                outcome.eventId(), outcome.orderId(), outcome.reason());
        return OrderSagaProcessingResult.APPLIED;
    }

    private OrderFailureReason mapFailureReason(InventoryReservationFailureReasonV1 reason) {
        return switch (reason) {
            case INSUFFICIENT_INVENTORY -> OrderFailureReason.INSUFFICIENT_INVENTORY;
            case ITEM_NOT_FOUND -> OrderFailureReason.INVENTORY_UNAVAILABLE;
            case REQUEST_CONFLICT -> OrderFailureReason.SYSTEM_ERROR;
        };
    }
}
