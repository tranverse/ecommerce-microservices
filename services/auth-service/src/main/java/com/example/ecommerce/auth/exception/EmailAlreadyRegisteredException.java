package com.example.ecommerce.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_REGISTERED,
                "An account with this email is already registered");
    }
}
