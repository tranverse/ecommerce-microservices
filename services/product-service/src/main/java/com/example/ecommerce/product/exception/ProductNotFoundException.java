package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(UUID productId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, "Product '%s' was not found".formatted(productId));
    }
}
