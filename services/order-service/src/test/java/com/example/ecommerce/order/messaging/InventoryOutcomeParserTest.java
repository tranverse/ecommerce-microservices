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

class InventoryOutcomeParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private InventoryOutcomeParser parser;

    @BeforeEach
    void setUp() {
        parser = new InventoryOutcomeParser(objectMapper);
    }

    @Test
    void parsesAValidInventoryReservedOutcome() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        InventoryOutcome outcome = parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        InventoryOutcomeParser.INVENTORY_RESERVED,
                        1,
                        Map.of("orderId", orderId, "reservationId", reservationId),
                        Map.of()
                )
        );

        assertThat(outcome).isInstanceOfSatisfying(InventoryReservedOutcome.class, reserved -> {
            assertThat(reserved.orderId()).isEqualTo(orderId);
            assertThat(reserved.reservationId()).isEqualTo(reservationId);
            assertThat(reserved.correlationId()).isEqualTo("order-inventory-parser-test");
        });
    }

    @Test
    void parsesAValidInventoryReservationFailure() throws Exception {
        UUID orderId = UUID.randomUUID();

        InventoryOutcome outcome = parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        InventoryOutcomeParser.INVENTORY_RESERVATION_FAILED,
                        1,
                        Map.of(
                                "orderId", orderId,
                                "reason", InventoryReservationFailureReasonV1.INSUFFICIENT_INVENTORY
                        ),
                        Map.of()
                )
        );

        assertThat(outcome).isInstanceOfSatisfying(
                InventoryReservationFailedOutcome.class,
                failed -> assertThat(failed.reason())
                        .isEqualTo(InventoryReservationFailureReasonV1.INSUFFICIENT_INVENTORY)
        );
    }

    @Test
    void rejectsAnUnsupportedVersion() throws Exception {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> parser.parse(
                orderId.toString(),
                outcomeJson(
                        orderId,
                        InventoryOutcomeParser.INVENTORY_RESERVED,
                        2,
                        Map.of("orderId", orderId, "reservationId", UUID.randomUUID()),
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
                InventoryOutcomeParser.INVENTORY_RESERVED,
                1,
                Map.of("orderId", orderId, "reservationId", UUID.randomUUID()),
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
                        InventoryOutcomeParser.INVENTORY_RESERVED,
                        1,
                        Map.of("orderId", orderId, "reservationId", UUID.randomUUID()),
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
                        InventoryOutcomeParser.INVENTORY_RESERVED,
                        1,
                        Map.of(
                                "orderId", orderId,
                                "reservationId", UUID.randomUUID(),
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
        envelope.put("correlationId", "order-inventory-parser-test");
        envelope.put("aggregateId", orderId);
        envelope.put("payload", payload);
        envelope.putAll(extraMetadata);
        return objectMapper.writeValueAsString(envelope);
    }
}
