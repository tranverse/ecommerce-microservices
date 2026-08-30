package com.example.ecommerce.payment.domain;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    private static final Pattern PROVIDER_REFERENCE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private PaymentFailureReason failureReason;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "refund_reference", length = 128)
    private String refundReference;

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(UUID orderId, BigDecimal amount, String currency) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.amount = normalizeAmount(amount);
        this.currency = normalizeCurrency(currency);
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(UUID orderId, BigDecimal amount, String currency) {
        return new Payment(orderId, amount, currency);
    }

    public boolean matches(BigDecimal requestedAmount, String requestedCurrency) {
        return amount.compareTo(normalizeAmount(requestedAmount)) == 0
                && currency.equals(normalizeCurrency(requestedCurrency));
    }

    public boolean complete(String reference) {
        String normalizedReference = normalizeReference(reference, "providerReference");
        if (status == PaymentStatus.COMPLETED) {
            if (!normalizedReference.equals(providerReference)) {
                throw new IllegalStateException("completed payment already has a different provider reference");
            }
            return false;
        }
        requireStatus(PaymentStatus.PENDING, "complete");
        status = PaymentStatus.COMPLETED;
        providerReference = normalizedReference;
        return true;
    }

    public boolean fail(PaymentFailureReason reason) {
        Objects.requireNonNull(reason, "failure reason must not be null");
        if (status == PaymentStatus.FAILED) {
            if (failureReason != reason) {
                throw new IllegalStateException("failed payment already has a different reason");
            }
            return false;
        }
        requireStatus(PaymentStatus.PENDING, "fail");
        status = PaymentStatus.FAILED;
        failureReason = reason;
        return true;
    }

    public boolean refund(String reference) {
        String normalizedReference = normalizeReference(reference, "refundReference");
        if (status == PaymentStatus.REFUNDED) {
            if (!normalizedReference.equals(refundReference)) {
                throw new IllegalStateException("refunded payment already has a different refund reference");
            }
            return false;
        }
        requireStatus(PaymentStatus.COMPLETED, "refund");
        status = PaymentStatus.REFUNDED;
        refundReference = normalizedReference;
        return true;
    }

    private void requireStatus(PaymentStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("cannot " + operation + " payment in status " + status);
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        Objects.requireNonNull(value, "amount must not be null");
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("amount must be positive with at most two decimal places");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > 17) {
            throw new IllegalArgumentException("amount exceeds the supported monetary range");
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        if (value == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency must be a valid ISO 4217 code", exception);
        }
    }

    private static String normalizeReference(String value, String field) {
        if (value == null || !PROVIDER_REFERENCE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentFailureReason getFailureReason() {
        return failureReason;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getRefundReference() {
        return refundReference;
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
