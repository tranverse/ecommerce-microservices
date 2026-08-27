package com.example.ecommerce.user.exception;

import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends ApiException {

    public ProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, "User profile was not found");
    }
}
