package com.example.ecommerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_sku", nullable = false, updatable = false, length = 64)
    private String productSku;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, int lineNumber, ProductSnapshot snapshot) {
        this.order = Objects.requireNonNull(order, "order must not be null");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        this.lineNumber = lineNumber;
        this.productId = Objects.requireNonNull(snapshot.productId(), "productId must not be null");
        this.productSku = requireText(snapshot.sku(), "sku");
        this.productName = requireText(snapshot.name(), "name");
        this.unitPrice = requirePrice(snapshot.unitPrice());
        if (snapshot.quantity() < 1 || snapshot.quantity() > 999) {
            throw new IllegalArgumentException("quantity must be between 1 and 999");
        }
        this.quantity = snapshot.quantity();
        normalizeCurrency(snapshot.currency());
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    static String normalizeCurrency(String value) {
        String currency = requireText(value, "currency").toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
        return currency;
    }

    private static BigDecimal requirePrice(BigDecimal value) {
        Objects.requireNonNull(value, "unitPrice must not be null");
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("unitPrice must be positive with at most two decimal places");
        }
        return value.setScale(2);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
