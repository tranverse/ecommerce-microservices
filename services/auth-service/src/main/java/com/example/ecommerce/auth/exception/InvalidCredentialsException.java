package com.example.ecommerce.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                "Email or password is invalid");
    }
}
