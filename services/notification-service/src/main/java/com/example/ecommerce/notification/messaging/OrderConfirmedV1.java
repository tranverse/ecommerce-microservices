package com.example.ecommerce.notification.messaging;

import java.util.UUID;

public record OrderConfirmedV1(UUID orderId, UUID customerId) {
}
