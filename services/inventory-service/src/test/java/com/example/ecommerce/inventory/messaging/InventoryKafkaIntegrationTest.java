package com.example.ecommerce.inventory.messaging;

import com.example.ecommerce.inventory.TestcontainersConfiguration;
import com.example.ecommerce.inventory.domain.ReservationStatus;
import com.example.ecommerce.inventory.repository.InventoryItemRepository;
import com.example.ecommerce.inventory.repository.InventoryReservationRepository;
import com.example.ecommerce.inventory.repository.OutboxEventRepository;
import com.example.ecommerce.inventory.repository.ProcessedEventRepository;
import com.example.ecommerce.inventory.service.InventoryService;
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
class InventoryKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "inventory-kafka-it-" + UUID.randomUUID();

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
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        reservationRepository.deleteAll();
        inventoryItemRepository.deleteAll();
    }

    @Test
    void consumesCommandsPublishesOutcomeAndIgnoresRedeliveryBeforeTheNextCommand() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 4);
        EventEnvelope<InventoryReservationRequestedV1> reservation = new EventEnvelope<>(
                UUID.randomUUID(),
                InventoryCommandParser.RESERVATION_REQUESTED,
                1,
                Instant.now(),
                "inventory-kafka-integration",
                orderId,
                new InventoryReservationRequestedV1(
                        orderId,
                        List.of(new InventoryReservationRequestedV1.Item(productId, 2)))
        );
        String reservationJson = objectMapper.writeValueAsString(reservation);

        send(orderId, reservationJson);
        await(() -> processedEventRepository.existsById(reservation.eventId()));

        List<ConsumerRecord<String, String>> outcomes = consumeOne(InventoryEventFactory.INVENTORY_EVENTS_TOPIC);
        assertThat(outcomes).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(orderId.toString());
            assertThat(readJson(record.value()).path("eventType").asText())
                    .isEqualTo(InventoryEventFactory.INVENTORY_RESERVED);
        });

        EventEnvelope<InventoryLifecycleRequestedV1> release = new EventEnvelope<>(
                UUID.randomUUID(),
                InventoryCommandParser.RELEASE_REQUESTED,
                1,
                Instant.now(),
                "inventory-kafka-integration",
                orderId,
                new InventoryLifecycleRequestedV1(orderId)
        );
        send(orderId, reservationJson);
        send(orderId, objectMapper.writeValueAsString(release));
        await(() -> processedEventRepository.existsById(release.eventId()));

        assertThat(inventoryService.getStock(productId).reservedQuantity()).isZero();
        assertThat(reservationRepository.findByOrderId(orderId)).get()
                .extracting(value -> value.getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    private void send(UUID orderId, String value) throws Exception {
        kafkaTemplate.send(
                        "inventory.commands.v1",
                        orderId.toString(),
                        value
                )
                .get(10, TimeUnit.SECONDS);
    }

    private List<ConsumerRecord<String, String>> consumeOne(String topic) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "inventory-outcome-it-" + UUID.randomUUID(),
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

    private com.fasterxml.jackson.databind.JsonNode readJson(String value) {
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
}
