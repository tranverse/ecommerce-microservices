package com.example.ecommerce.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void reservesReleasesAndConfirmsStockWithoutChangingTheWrongQuantity() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), 10);

        item.reserve(4);
        assertThat(item.getTotalQuantity()).isEqualTo(10);
        assertThat(item.getReservedQuantity()).isEqualTo(4);
        assertThat(item.getAvailableQuantity()).isEqualTo(6);

        item.release(1);
        assertThat(item.getTotalQuantity()).isEqualTo(10);
        assertThat(item.getReservedQuantity()).isEqualTo(3);

        item.confirm(2);
        assertThat(item.getTotalQuantity()).isEqualTo(8);
        assertThat(item.getReservedQuantity()).isEqualTo(1);
        assertThat(item.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void rejectsOversellingAndReducingTotalBelowReservations() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), 3);
        item.reserve(2);

        assertThatThrownBy(() -> item.reserve(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient");
        assertThatThrownBy(() -> item.setTotalQuantity(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservedQuantity");
    }

    @Test
    void rejectsNonPositiveMutationQuantities() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), 3);

        assertThatThrownBy(() -> item.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.release(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.confirm(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
