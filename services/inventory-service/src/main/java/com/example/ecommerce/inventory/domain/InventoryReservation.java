package com.example.ecommerce.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "inventory_reservations")
@EntityListeners(AuditingEntityListener.class)
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
    }

    private InventoryReservation(UUID orderId, Map<UUID, Integer> quantities) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        if (quantities == null || quantities.isEmpty()) {
            throw new IllegalArgumentException("reservation must contain at least one item");
        }
        this.status = ReservationStatus.RESERVED;
        quantities.forEach((productId, quantity) -> items.add(new ReservationItem(this, productId, quantity)));
    }

    public static InventoryReservation create(UUID orderId, Map<UUID, Integer> quantities) {
        return new InventoryReservation(orderId, quantities);
    }

    public boolean matches(Map<UUID, Integer> requestedQuantities) {
        Map<UUID, Integer> existing = items.stream().collect(Collectors.toUnmodifiableMap(
                ReservationItem::getProductId,
                ReservationItem::getQuantity
        ));
        return existing.equals(requestedQuantities);
    }

    public boolean release() {
        if (status == ReservationStatus.RELEASED) {
            return false;
        }
        requireStatus(ReservationStatus.RESERVED, "release");
        status = ReservationStatus.RELEASED;
        return true;
    }

    public boolean confirm() {
        if (status == ReservationStatus.CONFIRMED) {
            return false;
        }
        requireStatus(ReservationStatus.RESERVED, "confirm");
        status = ReservationStatus.CONFIRMED;
        return true;
    }

    private void requireStatus(ReservationStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("cannot " + operation + " reservation in status " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
