package com.example.ecommerce.inventory.messaging;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("messaging.consumer-reliability")
public record KafkaConsumerReliabilityProperties(
        @Min(0) int maxRetries,
        @NotNull @DurationMin(millis = 1) Duration initialInterval,
        @DecimalMin("1.0") double multiplier,
        @NotNull @DurationMin(millis = 1) Duration maxInterval,
        @NotNull @DurationMin(millis = 1) Duration recoveryTimeout
) {

    @AssertTrue(message = "max-interval must not be shorter than initial-interval")
    public boolean isIntervalRangeValid() {
        return initialInterval == null || maxInterval == null
                || maxInterval.compareTo(initialInterval) >= 0;
    }
}
