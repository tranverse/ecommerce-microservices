package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.TestcontainersConfiguration;
import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.exception.PaymentProcessorUnavailableException;
import com.example.ecommerce.payment.processor.ChargeRequest;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.PaymentProcessor;
import com.example.ecommerce.payment.repository.OutboxEventRepository;
import com.example.ecommerce.payment.repository.PaymentRepository;
import com.example.ecommerce.payment.repository.ProcessedEventRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "saga.messaging.listener-enabled=true",
        "outbox.publisher.enabled=true",
        "outbox.publisher.fixed-delay=PT0.1S"
})
@Import(TestcontainersConfiguration.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentSagaKafkaIntegrationTest {

    private static final String CONSUMER_GROUP = "payment-saga-kafka-it-" + UUID.randomUUID();

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
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private PaymentProcessor paymentProcessor;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        paymentRepository.deleteAll();
        doAnswer(invocation -> {
            ChargeRequest request = invocation.getArgument(0);
            return ChargeResult.approved("sim-charge-" + request.paymentId());
        }).when(paymentProcessor).charge(any());
    }

    @Test
    void consumesPaymentCommandPublishesOutcomeAndSuppressesSemanticRedelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<PaymentRequestedV1> first = paymentRequested(orderId);

        send(orderId, first);
        await(() -> processedEventRepository.existsById(first.eventId()));

        List<ConsumerRecord<String, String>> outcomes = consumeOne(PaymentEventFactory.PAYMENT_EVENTS_TOPIC);
        assertThat(outcomes).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(orderId.toString());
            assertThat(readJson(record.value()).path("eventType").asText())
                    .isEqualTo(PaymentEventFactory.PAYMENT_COMPLETED);
            assertThat(readJson(record.value()).path("correlationId").asText())
                    .isEqualTo(first.correlationId());
        });

        EventEnvelope<PaymentRequestedV1> semanticDuplicate = paymentRequested(orderId);
        send(orderId, semanticDuplicate);
        await(() -> processedEventRepository.existsById(semanticDuplicate.eventId()));

        assertThat(paymentRepository.findByOrderId(orderId)).get().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getProviderReference()).isEqualTo("sim-charge-" + payment.getId());
        });
        assertThat(processedEventRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void retriesTransientProcessorFailureAndThenCompletes() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<PaymentRequestedV1> event = paymentRequested(orderId);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            ChargeRequest request = invocation.getArgument(0);
            if (attempts.incrementAndGet() < 3) {
                throw new PaymentProcessorUnavailableException();
            }
            return ChargeResult.approved("sim-charge-" + request.paymentId());
        }).when(paymentProcessor).charge(any());

        send(orderId, event);

        await(() -> processedEventRepository.existsById(event.eventId()));
        assertThat(attempts).hasValue(3);
        assertThat(paymentRepository.findByOrderId(orderId)).get()
                .extracting(payment -> payment.getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void sendsExhaustedTransientFailureToDltAfterConfiguredRetries() throws Exception {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<PaymentRequestedV1> event = paymentRequested(orderId);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new PaymentProcessorUnavailableException();
        }).when(paymentProcessor).charge(any());

        send(orderId, event);

        ConsumerRecord<String, String> dlt = consumeMatching(
                "payment.commands.v1.DLT", orderId.toString());
        assertThat(dlt).isNotNull();
        assertThat(attempts).hasValue(4);
        assertThat(headerValue(dlt, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo("payment.commands.v1");
        assertThat(headerValue(dlt, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(PaymentProcessorUnavailableException.class.getName());
        assertThat(processedEventRepository.existsById(event.eventId())).isFalse();
        assertThat(paymentRepository.findByOrderId(orderId)).get()
                .extracting(payment -> payment.getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void sendsInvalidPaymentCommandDirectlyToDltWithOriginalMetadata() throws Exception {
        UUID orderId = UUID.randomUUID();
        String invalidValue = "{\"eventType\":\"UnknownCommand\"}";
        double failuresBefore = metricCount("ecommerce.kafka.consumer.delivery.failures",
                "payment.commands.v1");

        kafkaTemplate.send("payment.commands.v1", orderId.toString(), invalidValue)
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dlt = consumeMatching(
                "payment.commands.v1.DLT", orderId.toString());
        assertThat(dlt).isNotNull();
        assertThat(dlt.value()).isEqualTo(invalidValue);
        assertThat(headerValue(dlt, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo("payment.commands.v1");
        assertThat(headerValue(dlt, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(InvalidEventException.class.getName());
        assertThat(dlt.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).isNull();
        await(() -> metricCount("ecommerce.kafka.consumer.delivery.failures",
                "payment.commands.v1") >= failuresBefore + 1);
        assertThat(metricCount("ecommerce.kafka.consumer.delivery.failures",
                "payment.commands.v1") - failuresBefore).isEqualTo(1.0);
        assertThat(processedEventRepository.count()).isZero();
    }

    private EventEnvelope<PaymentRequestedV1> paymentRequested(UUID orderId) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                PaymentRequestedParser.PAYMENT_REQUESTED,
                1,
                Instant.now(),
                "payment-saga-kafka-integration",
                orderId,
                new PaymentRequestedV1(orderId, new BigDecimal("25.50"), "USD")
        );
    }

    private void send(UUID orderId, EventEnvelope<PaymentRequestedV1> event) throws Exception {
        kafkaTemplate.send(
                        "payment.commands.v1",
                        orderId.toString(),
                        objectMapper.writeValueAsString(event)
                )
                .get(10, TimeUnit.SECONDS);
    }

    private List<ConsumerRecord<String, String>> consumeOne(String topic) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "payment-outcome-it-" + UUID.randomUUID(),
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

    private ConsumerRecord<String, String> consumeMatching(String topic, String key) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "payment-dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(15);
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
