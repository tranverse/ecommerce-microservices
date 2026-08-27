package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ReservationNotFoundException extends ApiException {

    public ReservationNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, ErrorCode.RESERVATION_NOT_FOUND,
                "Inventory reservation for order '%s' was not found".formatted(orderId));
    }
}
