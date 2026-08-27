package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DuplicateReservationProductException extends ApiException {

    public DuplicateReservationProductException(UUID productId) {
        super(HttpStatus.BAD_REQUEST, ErrorCode.DUPLICATE_RESERVATION_PRODUCT,
                "Product '%s' occurs more than once in the reservation".formatted(productId));
    }
}
