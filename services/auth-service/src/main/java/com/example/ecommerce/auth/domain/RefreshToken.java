package com.example.ecommerce.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private AuthAccount account;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    private RefreshToken(AuthAccount account, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.account = Objects.requireNonNull(account, "account must not be null");
        this.tokenHash = requireHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static RefreshToken issue(AuthAccount account, String tokenHash, Instant expiresAt, Instant now) {
        return new RefreshToken(account, tokenHash, expiresAt, now);
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now) && account.isActive();
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "now must not be null");
        }
    }

    private static String requireHash(String hash) {
        if (hash == null || hash.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex value");
        }
        return hash;
    }

    public UUID getId() {
        return id;
    }

    public AuthAccount getAccount() {
        return account;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
