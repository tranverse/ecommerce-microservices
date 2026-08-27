package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ReservationConflictException extends ApiException {

    public ReservationConflictException(UUID orderId) {
        super(HttpStatus.CONFLICT, ErrorCode.RESERVATION_CONFLICT,
                "Order '%s' already has a reservation with different items".formatted(orderId));
    }
}
