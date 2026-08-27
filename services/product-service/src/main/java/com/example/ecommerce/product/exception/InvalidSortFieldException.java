package com.example.ecommerce.product.exception;

import org.springframework.http.HttpStatus;

import java.util.Collection;

public final class InvalidSortFieldException extends ApiException {

    public InvalidSortFieldException(String field, Collection<String> allowedFields) {
        super(
                ErrorCode.INVALID_SORT_FIELD,
                HttpStatus.BAD_REQUEST,
                "Unsupported sort field '%s'; allowed values are %s".formatted(field, allowedFields)
        );
    }
}
