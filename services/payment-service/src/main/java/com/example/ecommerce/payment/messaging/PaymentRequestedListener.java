package com.example.ecommerce.payment.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestedListener {

    private final PaymentRequestedParser parser;
    private final PaymentSagaMessageHandler handler;

    public PaymentRequestedListener(PaymentRequestedParser parser, PaymentSagaMessageHandler handler) {
        this.parser = parser;
        this.handler = handler;
    }

    @KafkaListener(
            topics = "${saga.messaging.payment-commands-topic:payment.commands.v1}",
            autoStartup = "${saga.messaging.listener-enabled:true}"
    )
    public void onPaymentRequested(ConsumerRecord<String, String> record) {
        PaymentRequestedMessage message = parser.parse(record.key(), record.value());
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", message.correlationId())) {
            handler.handle(message);
        }
    }
}
