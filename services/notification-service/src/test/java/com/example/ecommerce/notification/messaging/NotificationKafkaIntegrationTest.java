package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.TestcontainersConfiguration;
import com.example.ecommerce.notification.domain.NotificationStatus;
import com.example.ecommerce.notification.repository.CustomerNotificationRepository;
import com.example.ecommerce.notification.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(properties = "notification.messaging.listener-enabled=true")
@Import(TestcontainersConfiguration.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "notification-kafka-it-" + UUID.randomUUID();

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
    private CustomerNotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void consumesOrderEventAndSuppressesExactAndSemanticDuplicates() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        EventEnvelope<OrderConfirmedV1> first = confirmed(orderId, customerId);

        send(orderId, first);
        await(() -> processedEventRepository.existsById(first.eventId()));
        await(() -> notificationRepository.findAll().stream()
                .anyMatch(notification -> notification.getStatus() == NotificationStatus.SENT));

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getOrderId()).isEqualTo(orderId);
            assertThat(notification.getCustomerId()).isEqualTo(customerId);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttempts()).isEqualTo(1);
        });

        send(orderId, first);
        EventEnvelope<OrderConfirmedV1> semanticDuplicate = confirmed(orderId, customerId);
        send(orderId, semanticDuplicate);
        await(() -> processedEventRepository.existsById(semanticDuplicate.eventId()));

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttempts()).isEqualTo(1);
        });
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }

    @Test
    void sendsInvalidOrderEventDirectlyToDltWithOriginalMetadata() throws Exception {
        UUID orderId = UUID.randomUUID();
        String invalidValue = "{\"eventType\":\"UnknownOutcome\"}";
        double failuresBefore = metricCount("ecommerce.kafka.consumer.delivery.failures",
                "order.events.v1");

        kafkaTemplate.send("order.events.v1", orderId.toString(), invalidValue)
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dlt = consumeMatching(
                "order.events.v1.DLT", orderId.toString());
        assertThat(dlt).isNotNull();
        assertThat(dlt.value()).isEqualTo(invalidValue);
        assertThat(headerValue(dlt, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo("order.events.v1");
        assertThat(headerValue(dlt, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(InvalidEventException.class.getName());
        assertThat(dlt.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).isNull();
        await(() -> metricCount("ecommerce.kafka.consumer.delivery.failures",
                "order.events.v1") >= failuresBefore + 1);
        assertThat(metricCount("ecommerce.kafka.consumer.delivery.failures",
                "order.events.v1") - failuresBefore).isEqualTo(1.0);
        assertThat(processedEventRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    private EventEnvelope<OrderConfirmedV1> confirmed(UUID orderId, UUID customerId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                OrderLifecycleEventParser.ORDER_CONFIRMED,
                1,
                Instant.now(),
                "notification-kafka-integration",
                orderId,
                new OrderConfirmedV1(orderId, customerId)
        );
    }

    private void send(UUID orderId, EventEnvelope<OrderConfirmedV1> event) throws Exception {
        kafkaTemplate.send(
                        "order.events.v1",
                        orderId.toString(),
                        objectMapper.writeValueAsString(event)
                )
                .get(10, TimeUnit.SECONDS);
    }

    private ConsumerRecord<String, String> consumeMatching(String topic, String key) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "notification-dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(10);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        return null;
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private double metricCount(String name, String topic) {
        Counter counter = meterRegistry.find(name).tag("topic", topic).counter();
        return counter == null ? 0.0 : counter.count();
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
