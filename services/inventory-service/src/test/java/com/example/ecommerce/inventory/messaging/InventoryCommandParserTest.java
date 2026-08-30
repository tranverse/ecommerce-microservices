package com.example.ecommerce.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryCommandParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private InventoryCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new InventoryCommandParser(objectMapper);
    }

    @Test
    void parsesAValidReservationCommand() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String value = reservationJson(orderId, productId, 2, 1, Map.of());

        InventoryCommand command = parser.parse(orderId.toString(), value);

        assertThat(command).isInstanceOfSatisfying(ReservationRequestedCommand.class, reservation -> {
            assertThat(reservation.orderId()).isEqualTo(orderId);
            assertThat(reservation.items()).singleElement().satisfies(item -> {
                assertThat(item.productId()).isEqualTo(productId);
                assertThat(item.quantity()).isEqualTo(2);
            });
        });
    }

    @Test
    void rejectsAnUnsupportedVersion() throws Exception {
        UUID orderId = UUID.randomUUID();
        String value = reservationJson(orderId, UUID.randomUUID(), 1, 2, Map.of());

        assertThatThrownBy(() -> parser.parse(orderId.toString(), value))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Unsupported eventVersion");
    }

    @Test
    void rejectsAKeyThatDoesNotMatchTheOrderAggregate() throws Exception {
        UUID orderId = UUID.randomUUID();
        String value = reservationJson(orderId, UUID.randomUUID(), 1, 1, Map.of());

        assertThatThrownBy(() -> parser.parse(UUID.randomUUID().toString(), value))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Kafka key must equal orderId");
    }

    @Test
    void rejectsUnknownEnvelopeFieldsInsteadOfSilentlyIgnoringContractDrift() throws Exception {
        UUID orderId = UUID.randomUUID();
        String value = reservationJson(
                orderId, UUID.randomUUID(), 1, 1, Map.of("unexpected", true));

        assertThatThrownBy(() -> parser.parse(orderId.toString(), value))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Event is not a valid v1 envelope");
    }

    private String reservationJson(
            UUID orderId,
            UUID productId,
            int quantity,
            int version,
            Map<String, Object> extraMetadata
    ) throws Exception {
        var envelope = new java.util.LinkedHashMap<String, Object>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", InventoryCommandParser.RESERVATION_REQUESTED);
        envelope.put("eventVersion", version);
        envelope.put("occurredAt", Instant.parse("2026-01-01T00:00:00Z"));
        envelope.put("correlationId", "inventory-parser-test");
        envelope.put("aggregateId", orderId);
        envelope.put("payload", Map.of(
                "orderId", orderId,
                "items", List.of(Map.of("productId", productId, "quantity", quantity))
        ));
        envelope.putAll(extraMetadata);
        return objectMapper.writeValueAsString(envelope);
    }
}
