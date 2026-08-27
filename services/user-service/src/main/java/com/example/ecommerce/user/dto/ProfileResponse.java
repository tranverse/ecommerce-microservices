package com.example.ecommerce.user.dto;

import com.example.ecommerce.user.domain.ProfileStatus;
import com.example.ecommerce.user.domain.UserProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String displayName,
        String phone,
        ProfileStatus status,
        List<AddressResponse> addresses,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getDisplayName(),
                profile.getPhone(),
                profile.getStatus(),
                profile.getAddresses().stream().map(AddressResponse::from).toList(),
                profile.getVersion(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
