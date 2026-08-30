package com.example.ecommerce.order.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "order.auth")
public record OrderAuthProperties(
        @NotBlank String issuer,
        @NotBlank String jwksUri,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
