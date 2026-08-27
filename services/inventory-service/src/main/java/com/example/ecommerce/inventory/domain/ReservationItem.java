package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservation_items")
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private InventoryReservation reservation;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    protected ReservationItem() {
    }

    ReservationItem(InventoryReservation reservation, UUID productId, int quantity) {
        this.reservation = Objects.requireNonNull(reservation, "reservation must not be null");
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
