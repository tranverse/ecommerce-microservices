package com.example.ecommerce.inventory.service;

import com.example.ecommerce.inventory.dto.ReservationResponse;

public record ReservationOperationResult(ReservationResponse reservation, boolean created) {
}
