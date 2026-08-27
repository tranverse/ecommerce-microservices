package com.example.ecommerce.inventory.exception;

import com.example.ecommerce.inventory.domain.ReservationStatus;
import org.springframework.http.HttpStatus;

public class InvalidReservationStateException extends ApiException {

    public InvalidReservationStateException(ReservationStatus status, String operation) {
        super(HttpStatus.CONFLICT, ErrorCode.INVALID_RESERVATION_STATE,
                "Cannot %s a reservation in status %s".formatted(operation, status));
    }
}
