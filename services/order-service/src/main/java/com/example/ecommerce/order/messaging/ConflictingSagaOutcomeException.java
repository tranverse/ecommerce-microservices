package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.domain.OrderStatus;

import java.util.UUID;

public class ConflictingSagaOutcomeException extends RuntimeException {

    public ConflictingSagaOutcomeException(UUID orderId, OrderStatus status, String eventType) {
        super("Saga outcome " + eventType + " conflicts with order " + orderId + " in state " + status);
    }
}
