package com.example.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends ApiException {

    public IdempotencyKeyConflictException() {
        super(
                HttpStatus.CONFLICT,
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                "Idempotency-Key was already used with a different request"
        );
    }
}
