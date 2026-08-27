package com.example.ecommerce.product.controller;

import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.exception.ProductNotFoundException;
import com.example.ecommerce.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createsProductAndReturnsLocation() throws Exception {
        UUID productId = UUID.randomUUID();
        when(productService.createProduct(any())).thenReturn(response(productId));

        mockMvc.perform(post("/api/v1/products")
                        .header("X-Correlation-ID", "test-correlation-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "LAPTOP-001",
                                  "name": "Developer Laptop",
                                  "price": 1499.00,
                                  "currency": "USD",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/" + productId))
                .andExpect(header().string("X-Correlation-ID", "test-correlation-123"))
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("LAPTOP-001"));
    }

    @Test
    void returnsConsistentValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("X-Correlation-ID", "validation-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "bad sku",
                                  "name": "",
                                  "price": 0,
                                  "currency": "US",
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("validation-123"))
                .andExpect(jsonPath("$.path").value("/api/v1/products"))
                .andExpect(jsonPath("$.details[*].field", hasItem("sku")))
                .andExpect(jsonPath("$.details[*].field", hasItem("name")))
                .andExpect(jsonPath("$.details[*].field", hasItem("price")))
                .andExpect(jsonPath("$.details[*].field", hasItem("currency")));
    }

    @Test
    void returnsNotFoundWithoutLeakingStackTrace() throws Exception {
        UUID productId = UUID.randomUUID();
        when(productService.getProduct(productId)).thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsUnknownEndpointToNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ENDPOINT_NOT_FOUND"));
    }

    private ProductResponse response(UUID productId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(
                productId,
                "LAPTOP-001",
                "Developer Laptop",
                null,
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.ACTIVE,
                0,
                now,
                now
        );
    }
}
