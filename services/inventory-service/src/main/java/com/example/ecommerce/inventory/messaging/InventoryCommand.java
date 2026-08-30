package com.example.ecommerce.inventory.messaging;

import java.time.Instant;
import java.util.UUID;

public sealed interface InventoryCommand
        permits ReservationRequestedCommand, ReleaseRequestedCommand, ConfirmationRequestedCommand {

    UUID eventId();

    String eventType();

    Instant occurredAt();

    String correlationId();

    UUID orderId();
}
