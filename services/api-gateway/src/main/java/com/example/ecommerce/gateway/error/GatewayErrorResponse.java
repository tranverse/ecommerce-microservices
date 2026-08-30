package com.example.ecommerce.gateway.error;

import java.time.Instant;
import java.util.List;

public record GatewayErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        List<Object> details,
        String path,
        String correlationId
) {

    public GatewayErrorResponse {
        details = List.copyOf(details);
    }
}
