package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.TestcontainersConfiguration;
import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderStatus;
import com.example.ecommerce.order.domain.ProductSnapshot;
import com.example.ecommerce.order.repository.CustomerOrderRepository;
import com.example.ecommerce.order.repository.OutboxEventRepository;
import com.example.ecommerce.order.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(properties = {
        "saga.messaging.listener-enabled=true",
        "outbox.publisher.enabled=true",
        "outbox.publisher.fixed-delay=PT0.1S"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentOutcomeKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "order-payment-kafka-it-" + UUID.randomUUID();

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> CONSUMER_GROUP);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void consumesPaymentCompletionAndPublishesInventoryAndOrderEventsOnce() throws Exception {
        CustomerOrder order = orderRepository.saveAndFlush(paymentPendingOrder());
        EventEnvelope<PaymentCompletedV1> first = completedEvent(order.getId());

        send(order.getId(), first);
        await(() -> processedEventRepository.existsById(first.eventId()));

        ConsumerRecord<String, String> inventoryCommand = consumeOne(
                OrderEventFactory.INVENTORY_COMMANDS_TOPIC);
        ConsumerRecord<String, String> orderEvent = consumeOne(OrderEventFactory.ORDER_EVENTS_TOPIC);

        assertThat(inventoryCommand.key()).isEqualTo(order.getId().toString());
        assertThat(readJson(inventoryCommand.value()).path("eventType").asText())
                .isEqualTo(OrderEventFactory.INVENTORY_CONFIRMATION_REQUESTED);
        assertThat(readJson(orderEvent.value()).path("eventType").asText())
                .isEqualTo(OrderEventFactory.ORDER_CONFIRMED);
        assertThat(readJson(orderEvent.value()).path("correlationId").asText())
                .isEqualTo(first.correlationId());

        EventEnvelope<PaymentCompletedV1> semanticDuplicate = completedEvent(order.getId());
        send(order.getId(), semanticDuplicate);
        await(() -> processedEventRepository.existsById(semanticDuplicate.eventId()));

        assertThat(orderRepository.findById(order.getId())).get()
                .extracting(CustomerOrder::getStatus)
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    private EventEnvelope<PaymentCompletedV1> completedEvent(UUID orderId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                PaymentOutcomeParser.PAYMENT_COMPLETED,
                1,
                Instant.now(),
                "order-payment-kafka-integration",
                orderId,
                new PaymentCompletedV1(orderId, UUID.randomUUID())
        );
    }

    private void send(UUID orderId, EventEnvelope<PaymentCompletedV1> event) throws Exception {
        kafkaTemplate.send(
                        "payment.events.v1",
                        orderId.toString(),
                        objectMapper.writeValueAsString(event)
                )
                .get(10, TimeUnit.SECONDS);
    }

    private ConsumerRecord<String, String> consumeOne(String topic) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-outcome-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(10);
            while (received.isEmpty() && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(250)).forEach(received::add);
            }
        }
        assertThat(received).singleElement();
        return received.getFirst();
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("Kafka value is not valid JSON", exception);
        }
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(15);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        if (!condition.getAsBoolean()) {
            fail("Condition was not met before timeout");
        }
    }

    private CustomerOrder paymentPendingOrder() {
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(),
                "order-payment-kafka-key-001",
                "f".repeat(64),
                List.of(new ProductSnapshot(
                        UUID.randomUUID(),
                        "SKU-PAYMENT-KAFKA-001",
                        "Payment Kafka test product",
                        new BigDecimal("21.00"),
                        "USD",
                        1
                ))
        );
        order.markInventoryReserved();
        order.markPaymentPending();
        return order;
    }
}
