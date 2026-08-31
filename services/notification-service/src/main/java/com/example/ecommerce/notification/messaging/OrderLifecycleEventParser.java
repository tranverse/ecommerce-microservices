package com.example.ecommerce.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OrderLifecycleEventParser {

    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ObjectMapper objectMapper;

    public OrderLifecycleEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public OrderLifecycleEvent parse(String kafkaKey, String value) {
        EventEnvelope<JsonNode> envelope = readEnvelope(value);
        validateMetadata(envelope);
        return switch (envelope.eventType()) {
            case ORDER_CONFIRMED -> confirmed(kafkaKey, envelope);
            case ORDER_CANCELLED -> cancelled(kafkaKey, envelope);
            default -> throw new InvalidEventException("Unsupported order lifecycle eventType");
        };
    }

    private EventEnvelope<JsonNode> readEnvelope(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Event value must not be blank");
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidEventException("Event is not a valid v1 envelope", exception);
        }
    }

    private void validateMetadata(EventEnvelope<JsonNode> envelope) {
        if (envelope.eventType().isBlank() || envelope.eventType().length() > 100) {
            throw new InvalidEventException("Invalid eventType");
        }
        if (envelope.eventVersion() != 1) {
            throw new InvalidEventException("Unsupported eventVersion");
        }
        if (!SAFE_CORRELATION_ID.matcher(envelope.correlationId()).matches()) {
            throw new InvalidEventException("Invalid correlationId");
        }
        if (!envelope.payload().isObject()) {
            throw new InvalidEventException("Event payload must be an object");
        }
    }

    private OrderConfirmedEvent confirmed(String kafkaKey, EventEnvelope<JsonNode> envelope) {
        OrderConfirmedV1 payload = convert(envelope.payload(), OrderConfirmedV1.class);
        requireValidCustomer(payload.customerId());
        requireMatchingOrder(kafkaKey, envelope.aggregateId(), payload.orderId());
        return new OrderConfirmedEvent(
                envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                envelope.correlationId(), payload.orderId(), payload.customerId());
    }

    private OrderCancelledEvent cancelled(String kafkaKey, EventEnvelope<JsonNode> envelope) {
        OrderCancelledV1 payload = convert(envelope.payload(), OrderCancelledV1.class);
        requireValidCustomer(payload.customerId());
        requireMatchingOrder(kafkaKey, envelope.aggregateId(), payload.orderId());
        if (payload.reason() == null) {
            throw new InvalidEventException("cancellation reason must not be null");
        }
        return new OrderCancelledEvent(
                envelope.eventId(), envelope.eventType(), envelope.occurredAt(),
                envelope.correlationId(), payload.orderId(), payload.customerId(), payload.reason());
    }

    private void requireValidCustomer(UUID customerId) {
        if (customerId == null) {
            throw new InvalidEventException("customerId must not be null");
        }
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
