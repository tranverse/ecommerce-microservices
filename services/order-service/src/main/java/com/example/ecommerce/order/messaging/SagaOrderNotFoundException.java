package com.example.ecommerce.order.messaging;

import java.util.UUID;

public class SagaOrderNotFoundException extends RuntimeException {

    public SagaOrderNotFoundException(UUID orderId) {
        super("Order was not found while processing saga outcome: " + orderId);
    }
}
