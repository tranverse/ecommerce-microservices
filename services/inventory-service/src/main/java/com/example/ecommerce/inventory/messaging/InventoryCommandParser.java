package com.example.ecommerce.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class InventoryCommandParser {

    public static final String RESERVATION_REQUESTED = "InventoryReservationRequested";
    public static final String RELEASE_REQUESTED = "InventoryReleaseRequested";
    public static final String CONFIRMATION_REQUESTED = "InventoryConfirmationRequested";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ObjectMapper objectMapper;

    public InventoryCommandParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public InventoryCommand parse(String kafkaKey, String value) {
        EventEnvelope<JsonNode> envelope = readEnvelope(value);
        validateMetadata(envelope);
        return switch (envelope.eventType()) {
            case RESERVATION_REQUESTED -> reservationCommand(kafkaKey, envelope);
            case RELEASE_REQUESTED -> lifecycleCommand(kafkaKey, envelope, false);
            case CONFIRMATION_REQUESTED -> lifecycleCommand(kafkaKey, envelope, true);
            default -> throw new InvalidEventException("Unsupported inventory command eventType");
        };
    }

    private EventEnvelope<JsonNode> readEnvelope(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Event value must not be blank");
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new InvalidEventException("Event is not a valid v1 envelope", exception);
        }
    }

    private void validateMetadata(EventEnvelope<JsonNode> envelope) {
        if (envelope.eventId() == null || envelope.occurredAt() == null || envelope.aggregateId() == null) {
            throw new InvalidEventException("Event metadata is incomplete");
        }
        if (envelope.eventType() == null
                || envelope.eventType().isBlank()
                || envelope.eventType().length() > 100) {
            throw new InvalidEventException("Invalid eventType");
        }
        if (envelope.eventVersion() != 1) {
            throw new InvalidEventException("Unsupported eventVersion");
        }
        if (envelope.correlationId() == null
                || !SAFE_CORRELATION_ID.matcher(envelope.correlationId()).matches()) {
            throw new InvalidEventException("Invalid correlationId");
        }
        if (envelope.payload() == null || !envelope.payload().isObject()) {
            throw new InvalidEventException("Event payload must be an object");
        }
    }

    private ReservationRequestedCommand reservationCommand(
            String kafkaKey,
            EventEnvelope<JsonNode> envelope
    ) {
        InventoryReservationRequestedV1 payload = convert(
                envelope.payload(), InventoryReservationRequestedV1.class);
        requireMatchingOrder(kafkaKey, envelope.aggregateId(), payload.orderId());
        if (payload.items() == null || payload.items().isEmpty() || payload.items().size() > 50) {
            throw new InvalidEventException("Reservation must contain between 1 and 50 items");
        }
        Set<UUID> productIds = new HashSet<>();
        for (InventoryReservationRequestedV1.Item item : payload.items()) {
            if (item == null || item.productId() == null || item.quantity() == null
                    || item.quantity() < 1 || item.quantity() > 999) {
                throw new InvalidEventException("Reservation item is invalid");
            }
            if (!productIds.add(item.productId())) {
                throw new InvalidEventException("Reservation contains duplicate productId");
            }
        }
        return new ReservationRequestedCommand(
                envelope.eventId(),
                envelope.eventType(),
                envelope.occurredAt(),
                envelope.correlationId(),
                payload.orderId(),
                List.copyOf(payload.items())
        );
    }

    private InventoryCommand lifecycleCommand(
            String kafkaKey,
            EventEnvelope<JsonNode> envelope,
            boolean confirmation
    ) {
        InventoryLifecycleRequestedV1 payload = convert(
                envelope.payload(), InventoryLifecycleRequestedV1.class);
        requireMatchingOrder(kafkaKey, envelope.aggregateId(), payload.orderId());
        if (confirmation) {
            return new ConfirmationRequestedCommand(
                    envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                    envelope.correlationId(), payload.orderId());
        }
        return new ReleaseRequestedCommand(
                envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                envelope.correlationId(), payload.orderId());
    }

    private void requireMatchingOrder(String kafkaKey, UUID aggregateId, UUID orderId) {
        if (orderId == null || !orderId.equals(aggregateId)) {
            throw new InvalidEventException("payload.orderId must equal aggregateId");
        }
        if (kafkaKey == null || !orderId.toString().equals(kafkaKey)) {
            throw new InvalidEventException("Kafka key must equal orderId");
        }
    }

    private <T> T convert(JsonNode payload, Class<T> type) {
        try {
            return objectMapper.treeToValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new InvalidEventException("Event payload does not match eventType", exception);
        }
    }
}
