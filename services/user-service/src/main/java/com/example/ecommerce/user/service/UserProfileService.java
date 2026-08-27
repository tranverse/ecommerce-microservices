package com.example.ecommerce.user.service;

import com.example.ecommerce.user.domain.UserAddress;
import com.example.ecommerce.user.domain.UserProfile;
import com.example.ecommerce.user.dto.AddressRequest;
import com.example.ecommerce.user.dto.AddressResponse;
import com.example.ecommerce.user.dto.ProfileResponse;
import com.example.ecommerce.user.dto.ProfileUpsertRequest;
import com.example.ecommerce.user.exception.ProfileNotFoundException;
import com.example.ecommerce.user.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return ProfileResponse.from(requiredProfile(userId));
    }

    @Transactional
    public ProfileResponse upsertProfile(UUID userId, ProfileUpsertRequest request) {
        UserProfile profile = repository.findWithAddressesById(userId)
                .map(existing -> {
                    existing.update(request.displayName(), request.phone());
                    return existing;
                })
                .orElseGet(() -> UserProfile.create(userId, request.displayName(), request.phone()));
        UserProfile saved = repository.saveAndFlush(profile);
        log.info("Upserted user profile userId={} version={}", userId, saved.getVersion());
        return ProfileResponse.from(saved);
    }

    @Transactional
    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        UserProfile profile = requiredProfile(userId);
        UserAddress address = profile.addAddress(request.toDetails());
        repository.saveAndFlush(profile);
        log.info("Added user address userId={} addressId={}", userId, address.getId());
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        UserProfile profile = requiredProfile(userId);
        UserAddress address = profile.updateAddress(addressId, request.toDetails());
        repository.saveAndFlush(profile);
        log.info("Updated user address userId={} addressId={}", userId, addressId);
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        UserProfile profile = requiredProfile(userId);
        profile.removeAddress(addressId);
        repository.saveAndFlush(profile);
        log.info("Deleted user address userId={} addressId={}", userId, addressId);
    }

    private UserProfile requiredProfile(UUID userId) {
        return repository.findWithAddressesById(userId).orElseThrow(ProfileNotFoundException::new);
    }
}
