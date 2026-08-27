package com.example.ecommerce.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void generatesUniqueOpaqueValuesAndDeterministicHashes() {
        GeneratedRefreshToken first = generator.generate();
        GeneratedRefreshToken second = generator.generate();

        assertThat(first.rawValue()).isNotEqualTo(second.rawValue());
        assertThat(first.rawValue()).doesNotContain("=");
        assertThat(first.hash()).hasSize(64).isEqualTo(generator.hash(first.rawValue()));
        assertThat(first.hash()).doesNotContain(first.rawValue());
    }
}
