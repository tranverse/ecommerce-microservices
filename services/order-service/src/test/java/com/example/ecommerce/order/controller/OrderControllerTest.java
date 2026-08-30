package com.example.ecommerce.order.controller;

import com.example.ecommerce.order.config.OrderAuthProperties;
import com.example.ecommerce.order.config.SecurityConfiguration;
import com.example.ecommerce.order.domain.OrderStatus;
import com.example.ecommerce.order.dto.OrderItemResponse;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.OrderSummaryResponse;
import com.example.ecommerce.order.dto.PageResponse;
import com.example.ecommerce.order.security.SecurityErrorHandler;
import com.example.ecommerce.order.service.OrderApplicationService;
import com.example.ecommerce.order.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(OrderAuthProperties.class)
@TestPropertySource(properties = {
        "order.auth.issuer=http://localhost:8081",
        "order.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json",
        "order.auth.connect-timeout=PT2S",
        "order.auth.read-timeout=PT2S"
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsATokenWithoutAnAllowedRole() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void createsAnOrderForTheAuthenticatedCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderResponse response = response(customerId, productId);
        when(service.createOrder(eq(customerId), eq("checkout-key-001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .with(customer(customerId.toString()))
                        .header("Idempotency-Key", "checkout-key-001")
                        .header("X-Correlation-ID", "order-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":2}]}
                                """.formatted(productId)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/" + response.id()))
                .andExpect(header().string("X-Correlation-ID", "order-controller-test"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(service).createOrder(eq(customerId), eq("checkout-key-001"), any());
    }

    @Test
    void rejectsAMalformedAuthenticatedSubject() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(customer("not-a-uuid"))
                        .header("Idempotency-Key", "checkout-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IDENTITY"));
    }

    @Test
    void returnsAStableValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(customer(UUID.randomUUID().toString()))
                        .header("Idempotency-Key", "checkout-key-003")
                        .header("X-Correlation-ID", "order-validation-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("order-validation-test"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void delegatesOwnedDetailAndPaginationQueries() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(service.getOrder(customerId, orderId)).thenReturn(response(orderId, customerId, productId));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(service.listOrders(customerId, 1, 10)).thenReturn(new PageResponse<>(
                List.of(new OrderSummaryResponse(
                        orderId, OrderStatus.PENDING, null, "USD", new BigDecimal("20.00"), 0, now, now
                )),
                1, 10, 11, 2, false, true
        ));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).with(customer(customerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));

        mockMvc.perform(get("/api/v1/orders?page=1&size=10").with(customer(customerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(11));

        verify(service).getOrder(customerId, orderId);
        verify(service).listOrders(customerId, 1, 10);
    }

    private RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private OrderResponse response(UUID customerId, UUID productId) {
        return response(UUID.randomUUID(), customerId, productId);
    }

    private OrderResponse response(UUID orderId, UUID customerId, UUID productId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new OrderResponse(
                orderId,
                customerId,
                OrderStatus.PENDING,
                null,
                "USD",
                new BigDecimal("20.00"),
                List.of(new OrderItemResponse(
                        UUID.randomUUID(), 1, productId, "SKU-001", "Product One",
                        new BigDecimal("10.00"), 2, new BigDecimal("20.00")
                )),
                0,
                now,
                now
        );
    }
}
