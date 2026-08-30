package com.example.ecommerce.inventory.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, updatable = false, length = 100)
    private String consumerName;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    private ProcessedEvent(UUID eventId, String consumerName, String eventType, Instant processedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.consumerName = requireText(consumerName, "consumerName");
        this.eventType = requireText(eventType, "eventType");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt must not be null");
    }

    public static ProcessedEvent create(InventoryCommand command, String consumerName, Instant processedAt) {
        return new ProcessedEvent(command.eventId(), consumerName, command.eventType(), processedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException(field + " must contain between 1 and 100 characters");
        }
        return value;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
