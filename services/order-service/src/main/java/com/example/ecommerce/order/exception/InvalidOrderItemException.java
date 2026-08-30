package com.example.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class InvalidOrderItemException extends ApiException {

    public InvalidOrderItemException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INVALID_ORDER_ITEM, message);
    }
}
