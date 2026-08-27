package com.example.ecommerce.user.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void createsAndNormalizesAProfile() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.create(userId, "  Linh Nguyen  ", "+84901234567");

        assertThat(profile.getId()).isEqualTo(userId);
        assertThat(profile.getDisplayName()).isEqualTo("Linh Nguyen");
        assertThat(profile.getPhone()).isEqualTo("+84901234567");
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
    }

    @Test
    void rejectsInvalidPhoneAtTheDomainBoundary() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "Linh Nguyen", "0901234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void allowsOnlyOneDefaultAddressInTheAggregate() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Linh Nguyen", null);

        UserAddress home = profile.addAddress(address("HOME", true));
        UserAddress office = profile.addAddress(address("OFFICE", true));

        assertThat(home.isDefaultAddress()).isFalse();
        assertThat(office.isDefaultAddress()).isTrue();
        assertThat(profile.getAddresses()).hasSize(2);
        assertThat(office.getCountryCode()).isEqualTo("VN");
    }

    @Test
    void doesNotExposeAMutableAddressCollection() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "Linh Nguyen", null);

        assertThatThrownBy(() -> profile.getAddresses().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AddressDetails address(String label, boolean defaultAddress) {
        return new AddressDetails(
                label,
                "Linh Nguyen",
                "1 Nguyen Hue",
                null,
                "Ho Chi Minh City",
                null,
                "700000",
                "vn",
                "+84901234567",
                defaultAddress
        );
    }
}
