package com.example.ecommerce.order.messaging;

import java.util.UUID;

public record OrderConfirmedV1(UUID orderId, UUID customerId) {
}
