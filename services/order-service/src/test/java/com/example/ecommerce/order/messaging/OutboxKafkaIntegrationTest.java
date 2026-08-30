package com.example.ecommerce.order.messaging;

import com.example.ecommerce.order.TestcontainersConfiguration;
import com.example.ecommerce.order.repository.OutboxEventRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "outbox.publisher.enabled=false")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class OutboxKafkaIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxPublisherService publisherService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void publishesThePersistedJsonEnvelopeAndMarksTheRowAfterBrokerAcknowledgment() {
        OutboxEvent event = repository.saveAndFlush(event());

        assertThat(publisherService.publishBatch()).isEqualTo(1);

        OutboxEvent published = repository.findById(event.getId()).orElseThrow();
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getAttempts()).isEqualTo(1);

        List<ConsumerRecord<String, String>> records = consumeOne(event.getTopic());
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(event.getEventKey());
            assertThat(readJson(record.value())).isEqualTo(published.getPayload());
        });
    }

    private List<ConsumerRecord<String, String>> consumeOne(String topic) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-outbox-test-" + UUID.randomUUID(),
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
        return received;
    }

    private OutboxEvent event() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        EventEnvelope<InventoryReservationRequestedV1> envelope = new EventEnvelope<>(
                eventId,
                OrderEventFactory.INVENTORY_RESERVATION_REQUESTED,
                1,
                Instant.now(),
                "outbox-kafka-integration",
                orderId,
                new InventoryReservationRequestedV1(
                        orderId,
                        List.of(new InventoryReservationRequestedV1.Item(UUID.randomUUID(), 2))
                )
        );
        return OutboxEvent.create(
                envelope,
                "Order",
                OrderEventFactory.INVENTORY_COMMANDS_TOPIC,
                orderId.toString(),
                objectMapper.valueToTree(envelope)
        );
    }

    private com.fasterxml.jackson.databind.JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("Kafka value is not valid JSON", exception);
        }
    }
}
