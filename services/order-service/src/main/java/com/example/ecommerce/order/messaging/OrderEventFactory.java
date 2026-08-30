package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderFailureReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OrderEventFactory {

    public static final String INVENTORY_COMMANDS_TOPIC = "inventory.commands.v1";
    public static final String PAYMENT_COMMANDS_TOPIC = "payment.commands.v1";
    public static final String ORDER_EVENTS_TOPIC = "order.events.v1";
    public static final String INVENTORY_RESERVATION_REQUESTED = "InventoryReservationRequested";
    public static final String PAYMENT_REQUESTED = "PaymentRequested";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ObjectMapper objectMapper;

    public OrderEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent inventoryReservationRequested(CustomerOrder order) {
        var payload = new InventoryReservationRequestedV1(
                order.getId(),
                order.getItems().stream()
                        .map(item -> new InventoryReservationRequestedV1.Item(
                                item.getProductId(),
                                item.getQuantity()
                        ))
                        .toList()
        );
        EventEnvelope<InventoryReservationRequestedV1> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                INVENTORY_RESERVATION_REQUESTED,
                1,
                Instant.now(),
                currentCorrelationId(),
                order.getId(),
                payload
        );
        return OutboxEvent.create(
                envelope,
                "Order",
                INVENTORY_COMMANDS_TOPIC,
                order.getId().toString(),
                serialize(envelope)
        );
    }

    public OutboxEvent paymentRequested(CustomerOrder order, String correlationId) {
        return create(
                order,
                PAYMENT_REQUESTED,
                PAYMENT_COMMANDS_TOPIC,
                correlationId,
                new PaymentRequestedV1(order.getId(), order.getTotalAmount(), order.getCurrency())
        );
    }

    public OutboxEvent orderCancelled(CustomerOrder order, String correlationId) {
        OrderFailureReason failureReason = order.getFailureReason();
        if (failureReason == null) {
            throw new IllegalStateException("cancelled order must have a failure reason");
        }
        OrderCancellationReasonV1 eventReason = switch (failureReason) {
            case INVENTORY_UNAVAILABLE -> OrderCancellationReasonV1.INVENTORY_UNAVAILABLE;
            case INSUFFICIENT_INVENTORY -> OrderCancellationReasonV1.INSUFFICIENT_INVENTORY;
            case PAYMENT_FAILED -> OrderCancellationReasonV1.PAYMENT_FAILED;
            case SYSTEM_ERROR -> OrderCancellationReasonV1.SYSTEM_ERROR;
            case CUSTOMER_CANCELLED, PAYMENT_TIMEOUT ->
                    throw new IllegalArgumentException("failure reason is not supported by OrderCancelled v1");
        };
        return create(
                order,
                ORDER_CANCELLED,
                ORDER_EVENTS_TOPIC,
                correlationId,
                new OrderCancelledV1(order.getId(), order.getCustomerId(), eventReason)
        );
    }

    private OutboxEvent create(
            CustomerOrder order,
            String eventType,
            String topic,
            String correlationId,
            Object payload
    ) {
        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("correlationId has an invalid format");
        }
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                correlationId,
                order.getId(),
                payload
        );
        return OutboxEvent.create(
                envelope,
                "Order",
                topic,
                order.getId().toString(),
                serialize(envelope)
        );
    }

    private JsonNode serialize(EventEnvelope<?> envelope) {
        return objectMapper.valueToTree(envelope);
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
