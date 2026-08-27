package com.example.ecommerce.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("auth.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String keyId,
        String privateKeyBase64,
        String publicKeyBase64,
        boolean requireConfiguredKey
) {
}
