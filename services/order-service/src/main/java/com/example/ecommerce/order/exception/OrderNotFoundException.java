package com.example.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order was not found: " + orderId);
    }
}
