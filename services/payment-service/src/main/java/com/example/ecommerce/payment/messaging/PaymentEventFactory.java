package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.domain.Payment;
import com.example.ecommerce.payment.domain.PaymentFailureReason;
import com.example.ecommerce.payment.domain.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class PaymentEventFactory {

    public static final String PAYMENT_EVENTS_TOPIC = "payment.events.v1";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ObjectMapper objectMapper;

    public PaymentEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent outcome(Payment payment, String correlationId) {
        Object payload;
        String eventType;
        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            eventType = PAYMENT_COMPLETED;
            payload = new PaymentCompletedV1(payment.getOrderId(), payment.getId());
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            if (payment.getFailureReason() != PaymentFailureReason.DECLINED) {
                throw new IllegalStateException("failed payment has an unsupported failure reason");
            }
            eventType = PAYMENT_FAILED;
            payload = new PaymentFailedV1(
                    payment.getOrderId(), payment.getId(), PaymentFailureReasonV1.DECLINED);
        } else {
            throw new IllegalStateException("pending payment cannot produce a terminal outcome");
        }

        if (correlationId == null || !SAFE_CORRELATION_ID.matcher(correlationId).matches()) {
            throw new IllegalArgumentException("correlationId has an invalid format");
        }
        EventEnvelope<Object> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                correlationId,
                payment.getOrderId(),
                payload
        );
        JsonNode serialized = objectMapper.valueToTree(envelope);
        return OutboxEvent.create(
                envelope,
                "Payment",
                PAYMENT_EVENTS_TOPIC,
                payment.getOrderId().toString(),
                serialized
        );
    }
}
