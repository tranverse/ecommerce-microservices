package com.example.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@EntityListeners(AuditingEntityListener.class)
public class InventoryItem {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    private InventoryItem(UUID productId, int totalQuantity) {
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        requireNonNegative(totalQuantity, "totalQuantity");
        this.totalQuantity = totalQuantity;
    }

    public static InventoryItem create(UUID productId, int totalQuantity) {
        return new InventoryItem(productId, totalQuantity);
    }

    public void setTotalQuantity(int totalQuantity) {
        requireNonNegative(totalQuantity, "totalQuantity");
        if (totalQuantity < reservedQuantity) {
            throw new IllegalArgumentException("totalQuantity cannot be lower than reservedQuantity");
        }
        this.totalQuantity = totalQuantity;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > getAvailableQuantity()) {
            throw new IllegalStateException("insufficient available inventory");
        }
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
    }

    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("cannot release more inventory than is reserved");
        }
        reservedQuantity -= quantity;
    }

    public void confirm(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException("cannot confirm more inventory than is reserved");
        }
        reservedQuantity -= quantity;
        totalQuantity -= quantity;
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    public UUID getProductId() {
        return productId;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
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
