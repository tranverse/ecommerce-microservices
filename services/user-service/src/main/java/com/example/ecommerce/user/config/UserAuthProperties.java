package com.example.ecommerce.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "user.auth")
public record UserAuthProperties(
        @NotBlank String issuer,
        @NotBlank String jwksUri,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
