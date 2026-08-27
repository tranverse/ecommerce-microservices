package com.example.ecommerce.user.service;

import com.example.ecommerce.user.domain.UserProfile;
import com.example.ecommerce.user.dto.AddressRequest;
import com.example.ecommerce.user.dto.ProfileUpsertRequest;
import com.example.ecommerce.user.exception.ProfileNotFoundException;
import com.example.ecommerce.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository repository;

    @InjectMocks
    private UserProfileService service;

    @Test
    void returnsAnExistingProfile() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "Linh Nguyen", null);
        when(repository.findWithAddressesById(userId)).thenReturn(Optional.of(profile));

        assertThat(service.getProfile(userId).id()).isEqualTo(userId);
    }

    @Test
    void reportsAMissingProfile() {
        UUID userId = UUID.randomUUID();
        when(repository.findWithAddressesById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(userId))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void createsAProfileUsingTheAuthenticatedSubject() {
        UUID userId = UUID.randomUUID();
        when(repository.findWithAddressesById(userId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertProfile(userId, new ProfileUpsertRequest("Linh Nguyen", "+84901234567"));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
    }

    @Test
    void updatesAnExistingProfileWithoutChangingItsIdentity() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "Old Name", null);
        when(repository.findWithAddressesById(userId)).thenReturn(Optional.of(profile));
        when(repository.saveAndFlush(profile)).thenReturn(profile);

        var response = service.upsertProfile(
                userId,
                new ProfileUpsertRequest("New Name", "+84901234567")
        );

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.displayName()).isEqualTo("New Name");
    }

    @Test
    void addsAnAddressInsideTheProfileAggregate() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "Linh Nguyen", null);
        when(repository.findWithAddressesById(userId)).thenReturn(Optional.of(profile));
        when(repository.saveAndFlush(profile)).thenReturn(profile);

        service.addAddress(userId, addressRequest(true));

        assertThat(profile.getAddresses()).singleElement()
                .satisfies(address -> assertThat(address.isDefaultAddress()).isTrue());
        verify(repository).saveAndFlush(profile);
    }

    private AddressRequest addressRequest(boolean defaultAddress) {
        return new AddressRequest(
                "HOME",
                "Linh Nguyen",
                "1 Nguyen Hue",
                null,
                "Ho Chi Minh City",
                null,
                "700000",
                "VN",
                "+84901234567",
                defaultAddress
        );
    }
}
