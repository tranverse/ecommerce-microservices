package com.example.ecommerce.order.exception;

import org.springframework.http.HttpStatus;

public class ProductCatalogUnavailableException extends ApiException {

    public ProductCatalogUnavailableException() {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.PRODUCT_CATALOG_UNAVAILABLE,
                "Product catalog is temporarily unavailable"
        );
    }
}
