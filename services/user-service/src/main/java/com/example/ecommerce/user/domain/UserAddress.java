package com.example.ecommerce.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
@EntityListeners(AuditingEntityListener.class)
public class UserAddress {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false, updatable = false)
    private UserProfile profile;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 32)
    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Version
    @Column(nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAddress() {
    }

    static UserAddress create(UserProfile profile, AddressDetails details) {
        UserAddress address = new UserAddress();
        address.id = UUID.randomUUID();
        address.profile = Objects.requireNonNull(profile, "profile must not be null");
        address.update(details);
        return address;
    }

    void update(AddressDetails details) {
        Objects.requireNonNull(details, "details must not be null");
        this.label = UserProfile.requireText(details.label(), "label");
        this.recipientName = UserProfile.requireText(details.recipientName(), "recipientName");
        this.line1 = UserProfile.requireText(details.line1(), "line1");
        this.line2 = UserProfile.optionalText(details.line2());
        this.city = UserProfile.requireText(details.city(), "city");
        this.stateProvince = UserProfile.optionalText(details.stateProvince());
        this.postalCode = UserProfile.requireText(details.postalCode(), "postalCode");
        this.countryCode = normalizeCountryCode(details.countryCode());
        this.phone = UserProfile.normalizePhone(details.phone());
        this.defaultAddress = details.defaultAddress();
    }

    void changeDefault(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    private static String normalizeCountryCode(String value) {
        String countryCode = UserProfile.requireText(value, "countryCode").toUpperCase(Locale.ROOT);
        if (countryCode.length() != 2 || !countryCode.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException("countryCode must be a two-letter ISO code");
        }
        return countryCode;
    }

    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getStateProvince() {
        return stateProvince;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
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
