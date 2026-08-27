package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

public final class InvalidPriceRangeException extends ApiException {

    public InvalidPriceRangeException() {
        super(
                ErrorCode.INVALID_PRICE_RANGE,
                HttpStatus.BAD_REQUEST,
                "minimumPrice must be less than or equal to maximumPrice"
        );
    }
}
