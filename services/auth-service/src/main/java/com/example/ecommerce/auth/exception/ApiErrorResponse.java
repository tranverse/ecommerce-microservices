package com.example.ecommerce.auth.exception;

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
}
