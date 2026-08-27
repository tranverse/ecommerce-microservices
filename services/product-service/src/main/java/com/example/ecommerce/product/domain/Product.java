package com.example.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    private Product(String sku, String name, String description, BigDecimal price, ProductStatus status) {
        this.sku = normalizeSku(sku);
        this.name = requireText(name, "name");
        this.description = normalizeDescription(description);
        this.price = requirePositivePrice(price);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static Product create(
            String sku,
            String name,
            String description,
            BigDecimal price,
            ProductStatus status
    ) {
        return new Product(sku, name, description, price, status);
    }

    public void updateDetails(String name, String description, BigDecimal price, ProductStatus status) {
        this.name = requireText(name, "name");
        this.description = normalizeDescription(description);
        this.price = requirePositivePrice(price);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    private static String normalizeSku(String sku) {
        return requireText(sku, "sku").toUpperCase(Locale.ROOT);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal requirePositivePrice(BigDecimal price) {
        Objects.requireNonNull(price, "price must not be null");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        return price;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
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
