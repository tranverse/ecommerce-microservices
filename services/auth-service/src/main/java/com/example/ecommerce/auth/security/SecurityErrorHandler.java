package com.example.ecommerce.auth.security;

import com.example.ecommerce.auth.exception.ApiErrorResponse;
import com.example.ecommerce.auth.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        write(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, "Authentication is required or the token is invalid");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        write(response, request, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN, "You are not allowed to perform this operation");
    }

    private void write(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(), status, errorCode.name(), message, List.of(),
                request.getRequestURI(), MDC.get("correlationId")
        ));
    }
}
