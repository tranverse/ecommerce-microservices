package com.example.ecommerce.user.domain;

import java.util.UUID;

public class AddressNotOwnedException extends RuntimeException {

    public AddressNotOwnedException(UUID addressId) {
        super("Address not found: " + addressId);
    }
}
