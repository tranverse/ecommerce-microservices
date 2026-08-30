package com.example.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdentityException extends ApiException {

    public InvalidIdentityException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_IDENTITY, "Token subject is not a valid customer identity");
    }
}
