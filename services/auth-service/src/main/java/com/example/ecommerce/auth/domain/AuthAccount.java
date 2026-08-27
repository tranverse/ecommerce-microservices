package com.example.ecommerce.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "auth_accounts")
@EntityListeners(AuditingEntityListener.class)
public class AuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "auth_account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<AccountRole> roles = new LinkedHashSet<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthAccount() {
    }

    private AuthAccount(String email, String passwordHash, Set<AccountRole> roles) {
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("at least one role is required");
        }
        this.roles.addAll(roles);
        this.status = AccountStatus.ACTIVE;
    }

    public static AuthAccount registerCustomer(String email, String passwordHash) {
        return new AuthAccount(email, passwordHash, Set.of(AccountRole.CUSTOMER));
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void disable() {
        status = AccountStatus.DISABLED;
    }

    public static String normalizeEmail(String email) {
        return requireText(email, "email").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Set<AccountRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
