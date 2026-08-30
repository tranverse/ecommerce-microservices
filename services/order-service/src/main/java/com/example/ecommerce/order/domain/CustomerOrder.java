package com.example.ecommerce.order.domain;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "customer_orders")
@EntityListeners(AuditingEntityListener.class)
public class CustomerOrder {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern REQUEST_HASH = Pattern.compile("^[a-f0-9]{64}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private OrderFailureReason failureReason;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerOrder() {
    }

    private CustomerOrder(
            UUID customerId,
            String idempotencyKey,
            String requestHash,
            List<ProductSnapshot> snapshots
    ) {
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.idempotencyKey = validateIdempotencyKey(idempotencyKey);
        this.requestHash = validateRequestHash(requestHash);
        validateSnapshots(snapshots);
        this.currency = OrderItem.normalizeCurrency(snapshots.getFirst().currency());
        this.status = OrderStatus.PENDING;

        int lineNumber = 1;
        for (ProductSnapshot snapshot : snapshots) {
            if (!currency.equals(OrderItem.normalizeCurrency(snapshot.currency()))) {
                throw new IllegalArgumentException("all order items must use the same currency");
            }
            items.add(new OrderItem(this, lineNumber++, snapshot));
        }
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static CustomerOrder create(
            UUID customerId,
            String idempotencyKey,
            String requestHash,
            List<ProductSnapshot> snapshots
    ) {
        return new CustomerOrder(customerId, idempotencyKey, requestHash, snapshots);
    }

    public boolean markInventoryReserved() {
        if (status == OrderStatus.INVENTORY_RESERVED) {
            return false;
        }
        transition(OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED);
        return true;
    }

    public boolean markPaymentPending() {
        if (status == OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        transition(OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_PENDING);
        return true;
    }

    public boolean confirm() {
        if (status == OrderStatus.CONFIRMED) {
            return false;
        }
        transition(OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED);
        return true;
    }

    public boolean cancel(OrderFailureReason reason) {
        Objects.requireNonNull(reason, "failure reason must not be null");
        if (status == OrderStatus.CANCELLED) {
            if (failureReason != reason) {
                throw new IllegalStateException("cancelled order already has a different failure reason");
            }
            return false;
        }
        if (status == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("confirmed order cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        failureReason = reason;
        return true;
    }

    public boolean matchesRequestHash(String candidate) {
        return requestHash.equals(candidate);
    }

    private void transition(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            throw new IllegalStateException("cannot transition order from " + status + " to " + next);
        }
        status = next;
    }

    private static void validateSnapshots(List<ProductSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty() || snapshots.size() > 50) {
            throw new IllegalArgumentException("order must contain between 1 and 50 items");
        }
        Set<UUID> productIds = new HashSet<>();
        for (ProductSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "product snapshot must not be null");
            if (!productIds.add(snapshot.productId())) {
                throw new IllegalArgumentException("order must not contain duplicate products");
            }
        }
    }

    private static String validateIdempotencyKey(String value) {
        if (value == null || !IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("idempotencyKey has an invalid format");
        }
        return value;
    }

    private static String validateRequestHash(String value) {
        if (value == null || !REQUEST_HASH.matcher(value).matches()) {
            throw new IllegalArgumentException("requestHash has an invalid format");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderFailureReason getFailureReason() {
        return failureReason;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
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
