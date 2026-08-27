package com.example.ecommerce.user.dto;

import com.example.ecommerce.user.domain.UserAddress;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String recipientName,
        String line1,
        String line2,
        String city,
        String stateProvince,
        String postalCode,
        String countryCode,
        String phone,
        boolean defaultAddress,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getStateProvince(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.getPhone(),
                address.isDefaultAddress(),
                address.getVersion(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
