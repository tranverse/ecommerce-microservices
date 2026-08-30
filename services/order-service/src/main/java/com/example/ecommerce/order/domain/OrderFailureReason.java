package com.example.ecommerce.order.domain;

public enum OrderFailureReason {
    CUSTOMER_CANCELLED,
    INVENTORY_UNAVAILABLE,
    INSUFFICIENT_INVENTORY,
    PAYMENT_FAILED,
    PAYMENT_TIMEOUT,
    SYSTEM_ERROR
}
