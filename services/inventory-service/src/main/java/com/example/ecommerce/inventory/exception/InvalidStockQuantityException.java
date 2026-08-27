package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

public class InvalidStockQuantityException extends ApiException {

    public InvalidStockQuantityException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.INVALID_STOCK_QUANTITY, message);
    }
}
