package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublisherProperties properties;

    public OutboxPublisherService(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxPublisherProperties properties
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Transactional
    public int publishBatch() {
        int published = 0;
        for (OutboxEvent event : repository.lockUnpublishedBatch(properties.batchSize())) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload().toString())
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
                published++;
                log.info("Published outbox event eventId={} aggregateId={} eventType={} attempt={}",
                        event.getId(), event.getAggregateId(), event.getEventType(), event.getAttempts());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordFailure(event, exception);
                break;
            } catch (ExecutionException | TimeoutException exception) {
                recordFailure(event, exception);
            }
        }
        return published;
    }

    private void recordFailure(OutboxEvent event, Exception exception) {
        event.recordFailure(sanitizedError(exception), Instant.now().plus(retryDelay(event.getAttempts())));
        log.warn("Outbox publication failed eventId={} aggregateId={} eventType={} attempt={} cause={}",
                event.getId(), event.getAggregateId(), event.getEventType(), event.getAttempts(),
                exception.getClass().getSimpleName());
    }

    private Duration retryDelay(int completedAttempts) {
        int exponent = Math.min(Math.max(completedAttempts - 1, 0), 30);
        long multiplier = 1L << exponent;
        Duration candidate;
        try {
            candidate = properties.initialRetryDelay().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.maxRetryDelay();
        }
        return candidate.compareTo(properties.maxRetryDelay()) > 0
                ? properties.maxRetryDelay()
                : candidate;
    }

    private String sanitizedError(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = cause.getMessage();
        String value = cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        value = value.replace('\r', ' ').replace('\n', ' ');
        return value.substring(0, Math.min(value.length(), 500));
    }
}
