package com.example.ecommerce.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestedParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PaymentRequestedParser parser;

    @BeforeEach
    void setUp() {
        parser = new PaymentRequestedParser(objectMapper);
    }

    @Test
    void parsesAndNormalizesAValidPaymentCommand() throws Exception {
        UUID orderId = UUID.randomUUID();

        PaymentRequestedMessage message = parser.parse(
                orderId.toString(),
                paymentJson(orderId, new BigDecimal("25.5"), "USD", 1, Map.of(), Map.of())
        );

        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.amount()).isEqualByComparingTo("25.50");
        assertThat(message.currency()).isEqualTo("USD");
        assertThat(message.correlationId()).isEqualTo("payment-parser-test");
    }

    @Test
    void rejectsAnUnsupportedVersion() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                paymentJson(orderId, new BigDecimal("25.50"), "USD", 2, Map.of(), Map.of())
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Unsupported eventVersion");
    }

    @Test
    void rejectsAKeyThatDoesNotMatchTheOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        String value = paymentJson(
                orderId, new BigDecimal("25.50"), "USD", 1, Map.of(), Map.of());

        assertThatThrownBy(() -> parser.parse(UUID.randomUUID().toString(), value))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Kafka key must equal orderId");
    }

    @Test
    void rejectsUnknownEnvelopeFields() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                paymentJson(
                        orderId,
                        new BigDecimal("25.50"),
                        "USD",
                        1,
                        Map.of("unexpected", true),
                        Map.of()
                )
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Event is not a valid v1 envelope");
    }

    @Test
    void rejectsUnknownPayloadFields() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                paymentJson(
                        orderId,
                        new BigDecimal("25.50"),
                        "USD",
                        1,
                        Map.of(),
                        Map.of("unexpected", true)
                )
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Event payload does not match eventType");
    }

    @Test
    void rejectsInvalidMoneyAndCurrencyBeforeCallingTheDomain() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                paymentJson(orderId, new BigDecimal("1.001"), "USD", 1, Map.of(), Map.of())
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("amount must be positive with at most two decimal places");

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                paymentJson(orderId, new BigDecimal("1.00"), "usd", 1, Map.of(), Map.of())
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("currency must be an uppercase ISO 4217 code");
    }

    private String paymentJson(
            UUID orderId,
            BigDecimal amount,
            String currency,
            int version,
            Map<String, Object> extraMetadata,
            Map<String, Object> extraPayload
    ) throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("orderId", orderId);
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.putAll(extraPayload);

        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", PaymentRequestedParser.PAYMENT_REQUESTED);
        envelope.put("eventVersion", version);
        envelope.put("occurredAt", Instant.parse("2026-01-01T00:00:00Z"));
        envelope.put("correlationId", "payment-parser-test");
        envelope.put("aggregateId", orderId);
        envelope.put("payload", payload);
        envelope.putAll(extraMetadata);
        return objectMapper.writeValueAsString(envelope);
    }
}
