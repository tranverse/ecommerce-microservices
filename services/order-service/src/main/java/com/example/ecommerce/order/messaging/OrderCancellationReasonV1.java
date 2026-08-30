package com.example.ecommerce.order.messaging;

public enum OrderCancellationReasonV1 {
    INVENTORY_UNAVAILABLE,
    INSUFFICIENT_INVENTORY,
    PAYMENT_FAILED,
    SYSTEM_ERROR
}
