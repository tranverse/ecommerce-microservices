package com.example.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdentityException extends ApiException {

    public InvalidIdentityException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_IDENTITY, "Authenticated identity is invalid");
    }
}
