package com.example.ecommerce.user.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "user_profiles")
@EntityListeners(AuditingEntityListener.class)
public class UserProfile {

    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileStatus status;

    @OneToMany(mappedBy = "profile", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<UserAddress> addresses = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    private UserProfile(UUID id, String displayName, String phone) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayName = requireText(displayName, "displayName");
        this.phone = normalizePhone(phone);
        this.status = ProfileStatus.ACTIVE;
    }

    public static UserProfile create(UUID id, String displayName, String phone) {
        return new UserProfile(id, displayName, phone);
    }

    public void update(String displayName, String phone) {
        ensureActive();
        this.displayName = requireText(displayName, "displayName");
        this.phone = normalizePhone(phone);
    }

    public UserAddress addAddress(AddressDetails details) {
        ensureActive();
        if (details.defaultAddress()) {
            addresses.forEach(address -> address.changeDefault(false));
        }
        UserAddress address = UserAddress.create(this, details);
        addresses.add(address);
        return address;
    }

    public UserAddress updateAddress(UUID addressId, AddressDetails details) {
        ensureActive();
        UserAddress address = address(addressId);
        if (details.defaultAddress()) {
            addresses.forEach(existing -> existing.changeDefault(false));
        }
        address.update(details);
        return address;
    }

    public void removeAddress(UUID addressId) {
        ensureActive();
        UserAddress address = address(addressId);
        addresses.remove(address);
    }

    public UserAddress address(UUID addressId) {
        return addresses.stream()
                .filter(address -> Objects.equals(address.getId(), addressId))
                .findFirst()
                .orElseThrow(() -> new AddressNotOwnedException(addressId));
    }

    private void ensureActive() {
        if (status != ProfileStatus.ACTIVE) {
            throw new IllegalStateException("profile is not active");
        }
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String normalizePhone(String value) {
        String phone = optionalText(value);
        if (phone != null && !E164_PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("phone must use E.164 format");
        }
        return phone;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public ProfileStatus getStatus() {
        return status;
    }

    public List<UserAddress> getAddresses() {
        return Collections.unmodifiableList(addresses);
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
