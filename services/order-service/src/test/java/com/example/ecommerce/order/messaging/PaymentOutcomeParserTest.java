package com.example.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentOutcomeParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PaymentOutcomeParser parser;

    @BeforeEach
    void setUp() {
        parser = new PaymentOutcomeParser(objectMapper);
    }

    @Test
    void parsesAValidPaymentCompletedOutcome() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentOutcome outcome = parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        PaymentOutcomeParser.PAYMENT_COMPLETED,
                        1,
                        Map.of("orderId", orderId, "paymentId", paymentId),
                        Map.of()
                )
        );

        assertThat(outcome).isInstanceOfSatisfying(PaymentCompletedOutcome.class, completed -> {
            assertThat(completed.orderId()).isEqualTo(orderId);
            assertThat(completed.paymentId()).isEqualTo(paymentId);
            assertThat(completed.correlationId()).isEqualTo("order-payment-parser-test");
        });
    }

    @Test
    void parsesAValidPaymentFailure() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentOutcome outcome = parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        PaymentOutcomeParser.PAYMENT_FAILED,
                        1,
                        Map.of(
                                "orderId", orderId,
                                "paymentId", paymentId,
                                "reason", PaymentFailureReasonV1.DECLINED
                        ),
                        Map.of()
                )
        );

        assertThat(outcome).isInstanceOfSatisfying(PaymentFailedOutcome.class, failed -> {
            assertThat(failed.paymentId()).isEqualTo(paymentId);
            assertThat(failed.reason()).isEqualTo(PaymentFailureReasonV1.DECLINED);
        });
    }

    @Test
    void rejectsAnUnsupportedVersion() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        PaymentOutcomeParser.PAYMENT_COMPLETED,
                        2,
                        Map.of("orderId", orderId, "paymentId", UUID.randomUUID()),
                        Map.of()
                )
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Unsupported eventVersion");
    }

    @Test
    void rejectsAKeyThatDoesNotMatchTheOrderAggregate() throws Exception {
        UUID orderId = UUID.randomUUID();
        String value = outcomeJson(
                orderId,
                PaymentOutcomeParser.PAYMENT_COMPLETED,
                1,
                Map.of("orderId", orderId, "paymentId", UUID.randomUUID()),
                Map.of()
        );

        assertThatThrownBy(() -> parser.parse(UUID.randomUUID().toString(), value))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Kafka key must equal orderId");
    }

    @Test
    void rejectsUnknownEnvelopeFieldsInsteadOfIgnoringContractDrift() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        PaymentOutcomeParser.PAYMENT_COMPLETED,
                        1,
                        Map.of("orderId", orderId, "paymentId", UUID.randomUUID()),
                        Map.of("unexpected", true)
                )
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Event is not a valid v1 envelope");
    }

    @Test
    void rejectsUnknownPayloadFieldsInsteadOfIgnoringContractDrift() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        PaymentOutcomeParser.PAYMENT_FAILED,
                        1,
                        Map.of(
                                "orderId", orderId,
                                "paymentId", UUID.randomUUID(),
                                "reason", PaymentFailureReasonV1.DECLINED,
                                "unexpected", true
                        ),
                        Map.of()
                )
        )).isInstanceOf(InvalidEventException.class)
                .hasMessage("Event payload does not match eventType");
    }

    private String outcomeJson(
            UUID orderId,
            String eventType,
            int version,
            Map<String, Object> payload,
            Map<String, Object> extraMetadata
    ) throws Exception {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", version);
        envelope.put("occurredAt", Instant.parse("2026-01-01T00:00:00Z"));
        envelope.put("correlationId", "order-payment-parser-test");
        envelope.put("aggregateId", orderId);
        envelope.put("payload", payload);
        envelope.putAll(extraMetadata);
        return objectMapper.writeValueAsString(envelope);
    }
}
