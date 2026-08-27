package com.example.ecommerce.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID orderId,
        @NotEmpty @Size(max = 100) List<@Valid ReservationLineRequest> items
) {
}
