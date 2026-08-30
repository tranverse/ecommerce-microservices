package com.example.ecommerce.order.messaging;

import java.time.Instant;
import java.util.UUID;

public sealed interface InventoryOutcome
        permits InventoryReservedOutcome, InventoryReservationFailedOutcome {

    UUID eventId();

    String eventType();

    Instant occurredAt();

    String correlationId();

    UUID orderId();
}
