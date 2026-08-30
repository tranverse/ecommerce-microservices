package com.example.ecommerce.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "gateway.auth")
public record GatewayAuthProperties(
        @NotBlank String issuer,
        @NotBlank String jwksUri,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
