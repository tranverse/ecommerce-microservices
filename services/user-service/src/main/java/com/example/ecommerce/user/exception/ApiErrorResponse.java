package com.example.ecommerce.user.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        List<FieldViolation> details,
        String path,
        String correlationId
) {

    public ApiErrorResponse {
        details = List.copyOf(details);
    }
}
