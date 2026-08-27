package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class ProductVersionConflictException extends ApiException {

    public ProductVersionConflictException(UUID productId) {
        super(
                ErrorCode.PRODUCT_VERSION_CONFLICT,
                HttpStatus.CONFLICT,
                "Product '%s' was updated by another request; reload it and retry".formatted(productId)
        );
    }
}
