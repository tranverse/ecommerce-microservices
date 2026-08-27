package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InsufficientInventoryException extends ApiException {

    public InsufficientInventoryException(UUID productId, int requested, int available) {
        super(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_INVENTORY,
                "Product '%s' has %d available but %d was requested".formatted(productId, available, requested));
    }
}
