package com.example.ecommerce.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class CustomerNotification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, updatable = false, length = 500)
    private String content;

    @Column(name = "cancellation_reason", updatable = false, length = 40)
    private String cancellationReason;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CustomerNotification() {
    }

    private CustomerNotification(
            UUID id,
            UUID sourceEventId,
            UUID orderId,
            UUID customerId,
            NotificationType type,
            String content,
            String cancellationReason,
            String correlationId,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.channel = NotificationChannel.SYSTEM;
        this.status = NotificationStatus.PENDING;
        this.content = requireText(content, "content", 500);
        this.cancellationReason = cancellationReason;
        this.correlationId = requireText(correlationId, "correlationId", 128);
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static CustomerNotification pending(
            UUID sourceEventId,
            UUID orderId,
            UUID customerId,
            NotificationType type,
            String content,
            String cancellationReason,
            String correlationId,
            Instant now
    ) {
        if ((type == NotificationType.ORDER_CANCELLED) != (cancellationReason != null)) {
            throw new IllegalArgumentException("cancellationReason must match notification type");
        }
        return new CustomerNotification(
                UUID.randomUUID(), sourceEventId, orderId, customerId, type, content,
                cancellationReason, correlationId, now);
    }

    public void startAttempt(Instant now) {
        if (status == NotificationStatus.SENT) {
            throw new IllegalStateException("notification cannot start a new delivery attempt from " + status);
        }
        status = NotificationStatus.PROCESSING;
        attempts++;
        failureReason = null;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markSent(Instant now) {
        requireProcessing();
        status = NotificationStatus.SENT;
        sentAt = Objects.requireNonNull(now, "now must not be null");
        failureReason = null;
        updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        requireProcessing();
        status = NotificationStatus.FAILED;
        failureReason = requireText(reason, "failureReason", 500);
        sentAt = null;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void requireProcessing() {
        if (status != NotificationStatus.PROCESSING) {
            throw new IllegalStateException("notification must be PROCESSING");
        }
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " must contain between 1 and " + maximumLength + " characters");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public long getVersion() {
        return version;
    }
}
