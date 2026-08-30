package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.domain.CustomerOrder;
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
    public static final String INVENTORY_RESERVATION_REQUESTED = "InventoryReservationRequested";
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
