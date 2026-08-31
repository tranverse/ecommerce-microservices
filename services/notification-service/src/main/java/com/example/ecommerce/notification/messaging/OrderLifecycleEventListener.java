package com.example.ecommerce.notification.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderLifecycleEventListener {

    private final OrderLifecycleEventParser parser;
    private final OrderLifecycleEventHandler handler;

    public OrderLifecycleEventListener(
            OrderLifecycleEventParser parser,
            OrderLifecycleEventHandler handler
    ) {
        this.parser = parser;
        this.handler = handler;
    }

    @KafkaListener(
            topics = "${notification.messaging.order-events-topic:order.events.v1}",
            autoStartup = "${notification.messaging.listener-enabled:true}"
    )
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        OrderLifecycleEvent event = parser.parse(record.key(), record.value());
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.correlationId())) {
            handler.handle(event);
        }
    }
}
