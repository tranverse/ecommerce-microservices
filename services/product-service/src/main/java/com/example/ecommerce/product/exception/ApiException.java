package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    protected ApiException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
