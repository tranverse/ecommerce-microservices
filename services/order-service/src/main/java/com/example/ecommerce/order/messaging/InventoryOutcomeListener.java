package com.example.ecommerce.order.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryOutcomeListener {

    private final InventoryOutcomeParser parser;
    private final OrderSagaTransactionService transactionService;

    public InventoryOutcomeListener(
            InventoryOutcomeParser parser,
            OrderSagaTransactionService transactionService
    ) {
        this.parser = parser;
        this.transactionService = transactionService;
    }

    @KafkaListener(
            topics = "${saga.messaging.inventory-events-topic:inventory.events.v1}",
            autoStartup = "${saga.messaging.listener-enabled:true}"
    )
    public void onOutcome(ConsumerRecord<String, String> record) {
        InventoryOutcome outcome = parser.parse(record.key(), record.value());
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", outcome.correlationId())) {
            transactionService.handle(outcome);
        }
    }
}
