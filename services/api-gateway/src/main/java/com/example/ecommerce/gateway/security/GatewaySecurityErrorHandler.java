package com.example.ecommerce.gateway.security;

import com.example.ecommerce.gateway.error.GatewayErrorResponse;
import com.example.ecommerce.gateway.web.CorrelationIdWebFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class GatewaySecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public GatewaySecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required or the token is invalid");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, org.springframework.security.access.AccessDeniedException exception) {
        return write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You are not allowed to perform this operation");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE_NAME);
        GatewayErrorResponse body = new GatewayErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                List.of(),
                exchange.getRequest().getPath().value(),
                correlationId
        );
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
