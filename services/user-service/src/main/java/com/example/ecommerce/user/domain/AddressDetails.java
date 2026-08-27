package com.example.ecommerce.user.domain;

public record AddressDetails(
        String label,
        String recipientName,
        String line1,
        String line2,
        String city,
        String stateProvince,
        String postalCode,
        String countryCode,
        String phone,
        boolean defaultAddress
) {
}
