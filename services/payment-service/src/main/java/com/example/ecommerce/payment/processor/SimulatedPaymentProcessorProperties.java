package com.example.ecommerce.payment.processor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.simulator")
public record SimulatedPaymentProcessorProperties(
        boolean declinePayments,
        boolean unavailable
) {
}
