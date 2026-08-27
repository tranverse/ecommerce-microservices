package com.example.ecommerce.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN,
                "Refresh token is invalid, expired, or revoked");
    }
}
