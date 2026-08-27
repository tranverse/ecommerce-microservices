package com.example.ecommerce.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void usableTokenBecomesUnusableAfterRevocationOrExpiry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AuthAccount account = AuthAccount.registerCustomer("user@example.com", "hash");
        RefreshToken token = RefreshToken.issue(account, HASH, now.plusSeconds(60), now);

        assertThat(token.isUsableAt(now)).isTrue();
        token.revoke(now.plusSeconds(1));
        token.revoke(now.plusSeconds(2));

        assertThat(token.isUsableAt(now.plusSeconds(2))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void disabledAccountInvalidatesItsRefreshTokens() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AuthAccount account = AuthAccount.registerCustomer("user@example.com", "hash");
        RefreshToken token = RefreshToken.issue(account, HASH, now.plusSeconds(60), now);

        account.disable();

        assertThat(token.isUsableAt(now)).isFalse();
    }
}
