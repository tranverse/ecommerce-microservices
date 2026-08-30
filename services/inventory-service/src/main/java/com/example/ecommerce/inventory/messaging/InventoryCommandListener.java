package com.example.ecommerce.inventory.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandListener {

    private final InventoryCommandParser parser;
    private final InventorySagaMessageHandler handler;

    public InventoryCommandListener(
            InventoryCommandParser parser,
            InventorySagaMessageHandler handler
    ) {
        this.parser = parser;
        this.handler = handler;
    }

    @KafkaListener(
            topics = "${saga.messaging.inventory-commands-topic:inventory.commands.v1}",
            autoStartup = "${saga.messaging.listener-enabled:true}"
    )
    public void onCommand(ConsumerRecord<String, String> record) {
        InventoryCommand command = parser.parse(record.key(), record.value());
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", command.correlationId())) {
            handler.handle(command);
        }
    }
}
