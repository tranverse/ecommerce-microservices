package com.example.ecommerce.user.dto;

import com.example.ecommerce.user.domain.AddressDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String stateProvince,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter ISO code") String countryCode,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must use E.164 format") String phone,
        boolean defaultAddress
) {

    public AddressDetails toDetails() {
        return new AddressDetails(
                label,
                recipientName,
                line1,
                line2,
                city,
                stateProvince,
                postalCode,
                countryCode,
                phone,
                defaultAddress
        );
    }
}
