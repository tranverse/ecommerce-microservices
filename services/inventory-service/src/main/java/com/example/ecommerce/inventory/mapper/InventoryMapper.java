package com.example.ecommerce.inventory.mapper;

import com.example.ecommerce.inventory.domain.InventoryItem;
import com.example.ecommerce.inventory.domain.InventoryReservation;
import com.example.ecommerce.inventory.dto.InventoryResponse;
import com.example.ecommerce.inventory.dto.ReservationItemResponse;
import com.example.ecommerce.inventory.dto.ReservationResponse;

import java.util.Comparator;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryResponse toResponse(InventoryItem item) {
        return new InventoryResponse(
                item.getProductId(),
                item.getTotalQuantity(),
                item.getReservedQuantity(),
                item.getAvailableQuantity(),
                item.getVersion(),
                item.getUpdatedAt()
        );
    }

    public static ReservationResponse toResponse(InventoryReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getItems().stream()
                        .sorted(Comparator.comparing(item -> item.getProductId().toString()))
                        .map(item -> new ReservationItemResponse(item.getProductId(), item.getQuantity()))
                        .toList(),
                reservation.getVersion(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
