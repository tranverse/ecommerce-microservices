package com.example.ecommerce.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLifecycleEventParserTest {

    private ObjectMapper objectMapper;
    private OrderLifecycleEventParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        parser = new OrderLifecycleEventParser(objectMapper);
    }

    @Test
    void parsesOrderConfirmed() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        EventEnvelope<OrderConfirmedV1> envelope = envelope(
                OrderLifecycleEventParser.ORDER_CONFIRMED,
                orderId,
                new OrderConfirmedV1(orderId, customerId)
        );

        OrderLifecycleEvent parsed = parser.parse(
                orderId.toString(), objectMapper.writeValueAsString(envelope));

        assertThat(parsed).isInstanceOf(OrderConfirmedEvent.class);
        assertThat(parsed.customerId()).isEqualTo(customerId);
        assertThat(parsed.content()).contains(orderId.toString());
    }

    @Test
    void parsesOrderCancelled() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<OrderCancelledV1> envelope = envelope(
                OrderLifecycleEventParser.ORDER_CANCELLED,
                orderId,
                new OrderCancelledV1(
                        orderId, UUID.randomUUID(), OrderCancellationReasonV1.PAYMENT_FAILED)
        );

        OrderLifecycleEvent parsed = parser.parse(
                orderId.toString(), objectMapper.writeValueAsString(envelope));

        assertThat(parsed).isInstanceOf(OrderCancelledEvent.class);
        assertThat(parsed.cancellationReason()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void rejectsUnknownEventType() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<OrderConfirmedV1> envelope = envelope(
                "PaymentCompleted", orderId, new OrderConfirmedV1(orderId, UUID.randomUUID()));

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(), objectMapper.writeValueAsString(envelope)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Unsupported order lifecycle eventType");
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<OrderConfirmedV1> envelope = new EventEnvelope<>(
                UUID.randomUUID(), OrderLifecycleEventParser.ORDER_CONFIRMED, 2,
                Instant.now(), "notification-parser-test", orderId,
                new OrderConfirmedV1(orderId, UUID.randomUUID()));

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(), objectMapper.writeValueAsString(envelope)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Unsupported eventVersion");
    }

    @Test
    void rejectsMismatchedKafkaKey() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<OrderConfirmedV1> envelope = envelope(
                OrderLifecycleEventParser.ORDER_CONFIRMED,
                orderId,
                new OrderConfirmedV1(orderId, UUID.randomUUID())
        );

        assertThatThrownBy(() -> parser.parse(
                UUID.randomUUID().toString(), objectMapper.writeValueAsString(envelope)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Kafka key must equal orderId");
    }

    @Test
    void rejectsUnknownPayloadField() {
        UUID orderId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "OrderConfirmed",
                  "eventVersion": 1,
                  "occurredAt": "2026-08-31T10:00:00Z",
                  "correlationId": "notification-parser-test",
                  "aggregateId": "%s",
                  "payload": {
                    "orderId": "%s",
                    "customerId": "%s",
                    "unexpected": true
                  }
                }
                """.formatted(UUID.randomUUID(), orderId, orderId, UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(orderId.toString(), json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Event payload does not match eventType");
    }

    private <T> EventEnvelope<T> envelope(String eventType, UUID orderId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(), eventType, 1, Instant.now(),
                "notification-parser-test", orderId, payload);
    }
}
