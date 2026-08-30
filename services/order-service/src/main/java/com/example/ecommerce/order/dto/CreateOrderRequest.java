package com.example.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid CreateOrderItemRequest> items
) {

    public CreateOrderRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
