package com.example.ecommerce.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryEventFactory {

    public static final String INVENTORY_EVENTS_TOPIC = "inventory.events.v1";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVATION_FAILED = "InventoryReservationFailed";

    private final ObjectMapper objectMapper;

    public InventoryEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent reservationSucceeded(ReservationRequestedCommand command, UUID reservationId) {
        return create(
                command,
                INVENTORY_RESERVED,
                new InventoryReservedV1(command.orderId(), reservationId)
        );
    }

    public OutboxEvent reservationFailed(
            ReservationRequestedCommand command,
            InventoryReservationFailureReason reason
    ) {
        return create(
                command,
                INVENTORY_RESERVATION_FAILED,
                new InventoryReservationFailedV1(command.orderId(), reason)
        );
    }

    private OutboxEvent create(InventoryCommand command, String eventType, Object payload) {
        Instant occurredAt = Clock.systemUTC().instant();
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                occurredAt,
                command.correlationId(),
                command.orderId(),
                payload
        );
        return OutboxEvent.create(
                envelope,
                "InventoryReservation",
                INVENTORY_EVENTS_TOPIC,
                command.orderId().toString(),
                objectMapper.valueToTree(envelope)
        );
    }
}
