package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

public final class ProductSkuConflictException extends ApiException {

    public ProductSkuConflictException(String sku) {
        super(ErrorCode.PRODUCT_SKU_CONFLICT, HttpStatus.CONFLICT, "Product SKU '%s' already exists".formatted(sku));
    }
}
