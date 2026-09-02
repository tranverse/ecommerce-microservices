package com.example.ecommerce.product.cache;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("product.cache")
public record ProductCacheProperties(
        boolean enabled,
        @NotBlank String keyPrefix,
        @NotNull @DurationMin(seconds = 1) Duration ttl
) {
}
