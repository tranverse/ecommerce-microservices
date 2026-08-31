package com.example.ecommerce.payment.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class PaymentRequestedParser {

    public static final String PAYMENT_REQUESTED = "PaymentRequested";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ObjectMapper objectMapper;

    public PaymentRequestedParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public PaymentRequestedMessage parse(String kafkaKey, String value) {
        EventEnvelope<JsonNode> envelope = readEnvelope(value);
        validateMetadata(envelope);
        PaymentRequestedV1 payload = convert(envelope.payload());
        requireMatchingOrder(kafkaKey, envelope.aggregateId(), payload.orderId());
        validateAmount(payload.amount());
        String currency = validateCurrency(payload.currency());
        return new PaymentRequestedMessage(
                envelope.eventId(),
                envelope.eventType(),
                envelope.occurredAt(),
                envelope.correlationId(),
                payload.orderId(),
                payload.amount().setScale(2),
                currency
        );
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
        if (!PAYMENT_REQUESTED.equals(envelope.eventType())) {
            throw new InvalidEventException("Unsupported payment command eventType");
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

    private PaymentRequestedV1 convert(JsonNode payload) {
        try {
            return objectMapper.treeToValue(payload, PaymentRequestedV1.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidEventException("Event payload does not match eventType", exception);
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

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidEventException("amount must be positive with at most two decimal places");
        }
        BigDecimal normalized = amount.setScale(2);
        if (normalized.precision() - normalized.scale() > 17) {
            throw new InvalidEventException("amount exceeds the supported monetary range");
        }
    }

    private String validateCurrency(String value) {
        if (value == null || !value.matches("^[A-Z]{3}$")) {
            throw new InvalidEventException("currency must be an uppercase ISO 4217 code");
        }
        try {
            return Currency.getInstance(value.toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new InvalidEventException("currency must be an uppercase ISO 4217 code", exception);
        }
    }
}
