package com.example.ecommerce.inventory.controller;

import com.example.ecommerce.inventory.domain.ReservationStatus;
import com.example.ecommerce.inventory.dto.InventoryResponse;
import com.example.ecommerce.inventory.dto.ReservationItemResponse;
import com.example.ecommerce.inventory.dto.ReservationResponse;
import com.example.ecommerce.inventory.exception.InventoryItemNotFoundException;
import com.example.ecommerce.inventory.service.InventoryService;
import com.example.ecommerce.inventory.service.ReservationOperationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void setsStockAndPropagatesCorrelationId() throws Exception {
        UUID productId = UUID.randomUUID();
        when(inventoryService.setStock(productId, 10))
                .thenReturn(new InventoryResponse(productId, 10, 0, 10, 0, Instant.now()));

        mockMvc.perform(put("/api/v1/inventory/items/{productId}", productId)
                        .header("X-Correlation-ID", "inventory-test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalQuantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "inventory-test-123"))
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void createsAReservationWithLocation() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ReservationResponse response = reservation(orderId, productId);
        when(inventoryService.reserve(any())).thenReturn(new ReservationOperationResult(response, true));

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "items": [{"productId": "%s", "quantity": 2}]
                                }
                                """.formatted(orderId, productId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/inventory/reservations/" + orderId))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void returnsOkForAnIdempotentReservationRetry() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(inventoryService.reserve(any()))
                .thenReturn(new ReservationOperationResult(reservation(orderId, productId), false));

        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","items":[{"productId":"%s","quantity":2}]}
                                """.formatted(orderId, productId)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnInvalidReservationBodyConsistently() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/reservations")
                        .header("X-Correlation-ID", "invalid-reservation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("invalid-reservation"))
                .andExpect(jsonPath("$.details[*].field", hasItem("orderId")))
                .andExpect(jsonPath("$.details[*].field", hasItem("items")));
    }

    @Test
    void returnsNotFoundWithoutAStackTrace() throws Exception {
        UUID productId = UUID.randomUUID();
        when(inventoryService.getStock(productId)).thenThrow(new InventoryItemNotFoundException(productId));

        mockMvc.perform(get("/api/v1/inventory/items/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private ReservationResponse reservation(UUID orderId, UUID productId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ReservationResponse(
                UUID.randomUUID(), orderId, ReservationStatus.RESERVED,
                List.of(new ReservationItemResponse(productId, 2)), 0, now, now);
    }
}
