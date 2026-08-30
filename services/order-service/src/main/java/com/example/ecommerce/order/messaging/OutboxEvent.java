package com.example.ecommerce.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final Pattern SAFE_TEXT = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, updatable = false, length = 128)
    private String topic;

    @Column(name = "event_key", nullable = false, updatable = false, length = 128)
    private String eventKey;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    private OutboxEvent(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String topic,
            String eventKey,
            String eventType,
            int eventVersion,
            JsonNode payload,
            String correlationId,
            Instant occurredAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.aggregateType = requireSafeText(aggregateType, "aggregateType");
        this.topic = requireSafeText(topic, "topic");
        this.eventKey = requireSafeText(eventKey, "eventKey");
        this.eventType = requireSafeText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventVersion = eventVersion;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.correlationId = requireSafeText(correlationId, "correlationId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.nextAttemptAt = occurredAt;
    }

    public static OutboxEvent create(
            EventEnvelope<?> envelope,
            String aggregateType,
            String topic,
            String eventKey,
            JsonNode payload
    ) {
        return new OutboxEvent(
                envelope.eventId(),
                envelope.aggregateId(),
                aggregateType,
                topic,
                eventKey,
                envelope.eventType(),
                envelope.eventVersion(),
                payload,
                envelope.correlationId(),
                envelope.occurredAt()
        );
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt != null) {
            return;
        }
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        this.attempts = Math.incrementExact(attempts);
        this.lastError = null;
    }

    public void recordFailure(String error, Instant nextAttemptAt) {
        if (publishedAt != null) {
            throw new IllegalStateException("published event cannot record a failure");
        }
        this.attempts = Math.incrementExact(attempts);
        if (error == null || error.isBlank() || error.length() > 500) {
            throw new IllegalArgumentException("error must contain between 1 and 500 characters");
        }
        this.lastError = error;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        if (this.nextAttemptAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("nextAttemptAt must not precede occurredAt");
        }
    }

    private static String requireSafeText(String value, String field) {
        if (value == null || !SAFE_TEXT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
