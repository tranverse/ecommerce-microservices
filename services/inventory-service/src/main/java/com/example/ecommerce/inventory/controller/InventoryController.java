package com.example.ecommerce.inventory.controller;

import com.example.ecommerce.inventory.dto.CreateReservationRequest;
import com.example.ecommerce.inventory.dto.InventoryResponse;
import com.example.ecommerce.inventory.dto.ReservationResponse;
import com.example.ecommerce.inventory.dto.SetStockRequest;
import com.example.ecommerce.inventory.service.InventoryService;
import com.example.ecommerce.inventory.service.ReservationOperationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
@Tag(name = "Inventory", description = "Stock and reservation lifecycle")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Create or replace total stock for a product")
    public InventoryResponse setStock(
            @PathVariable UUID productId,
            @Valid @RequestBody SetStockRequest request
    ) {
        return inventoryService.setStock(productId, request.totalQuantity());
    }

    @GetMapping("/items/{productId}")
    @Operation(summary = "Get stock availability for a product")
    public InventoryResponse getStock(@PathVariable UUID productId) {
        return inventoryService.getStock(productId);
    }

    @PostMapping("/reservations")
    @Operation(summary = "Atomically reserve stock for an order")
    public ResponseEntity<ReservationResponse> reserve(
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationOperationResult result = inventoryService.reserve(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.reservation());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(result.reservation().orderId())
                .toUri();
        return ResponseEntity.created(location).body(result.reservation());
    }

    @GetMapping("/reservations/{orderId}")
    @Operation(summary = "Get the inventory reservation for an order")
    public ReservationResponse getReservation(@PathVariable UUID orderId) {
        return inventoryService.getReservation(orderId);
    }

    @PostMapping("/reservations/{orderId}/release")
    @Operation(summary = "Release a pending reservation")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return inventoryService.release(orderId);
    }

    @PostMapping("/reservations/{orderId}/confirm")
    @Operation(summary = "Confirm consumption of reserved stock")
    public ReservationResponse confirm(@PathVariable UUID orderId) {
        return inventoryService.confirm(orderId);
    }
}
