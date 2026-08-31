package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.TestcontainersConfiguration;
import com.example.ecommerce.notification.domain.NotificationStatus;
import com.example.ecommerce.notification.repository.CustomerNotificationRepository;
import com.example.ecommerce.notification.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
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
