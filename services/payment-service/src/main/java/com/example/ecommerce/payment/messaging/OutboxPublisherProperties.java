package com.example.ecommerce.payment.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("outbox.publisher")
public record OutboxPublisherProperties(
        @Min(1) int batchSize,
        @NotNull Duration sendTimeout,
        @NotNull Duration initialRetryDelay,
        @NotNull Duration maxRetryDelay
) {
    public OutboxPublisherProperties {
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("sendTimeout must be positive");
        }
        if (initialRetryDelay == null || initialRetryDelay.isZero() || initialRetryDelay.isNegative()) {
            throw new IllegalArgumentException("initialRetryDelay must be positive");
        }
        if (maxRetryDelay == null || maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("maxRetryDelay must not be shorter than initialRetryDelay");
        }
    }
}
