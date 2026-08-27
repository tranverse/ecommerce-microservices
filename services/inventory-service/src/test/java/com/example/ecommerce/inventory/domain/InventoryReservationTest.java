package com.example.ecommerce.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryReservationTest {

    @Test
    void recognizesAnIdempotentReservationPayload() {
        UUID productId = UUID.randomUUID();
        InventoryReservation reservation = InventoryReservation.create(
                UUID.randomUUID(), Map.of(productId, 2));

        assertThat(reservation.matches(Map.of(productId, 2))).isTrue();
        assertThat(reservation.matches(Map.of(productId, 3))).isFalse();
    }

    @Test
    void onlyAllowsAReservationToReachOneTerminalState() {
        InventoryReservation reservation = InventoryReservation.create(
                UUID.randomUUID(), Map.of(UUID.randomUUID(), 1));

        assertThat(reservation.release()).isTrue();
        assertThat(reservation.release()).isFalse();
        assertThatThrownBy(reservation::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASED");
    }
}
