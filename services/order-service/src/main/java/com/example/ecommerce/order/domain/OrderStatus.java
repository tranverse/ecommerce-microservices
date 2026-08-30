package com.example.ecommerce.order.domain;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    CANCELLED
}
