package com.example.ecommerce.order.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.hibernate.validator.constraints.time.DurationMin;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "order.product-catalog")
public record ProductCatalogProperties(
        @NotBlank String baseUrl,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @NotNull @Valid Resilience resilience
) {

    public record Resilience(
            @Min(1) @Max(3) int maxAttempts,
            @NotNull @DurationMin(millis = 1) Duration retryWaitDuration,
            @DecimalMin("1.0") @DecimalMax("100.0") float failureRateThreshold,
            @Min(2) int slidingWindowSize,
            @Min(1) int minimumNumberOfCalls,
            @Min(1) int permittedCallsInHalfOpenState,
            @NotNull @DurationMin(millis = 1) Duration openStateWaitDuration
    ) {

        @AssertTrue(message = "minimum-number-of-calls must not exceed sliding-window-size")
        public boolean isWindowConfigurationValid() {
            return minimumNumberOfCalls <= slidingWindowSize;
        }
    }
}
