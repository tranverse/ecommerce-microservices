package com.example.ecommerce.inventory.messaging;

import com.example.ecommerce.inventory.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void marksAnAcknowledgedEventPublished() {
        OutboxEvent event = event();
        when(repository.lockUnpublishedBatch(10)).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> acknowledged = new CompletableFuture<>();
        acknowledged.complete(null);
        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload().toString()))
                .thenReturn(acknowledged);

        assertThat(service().publishBatch()).isEqualTo(1);

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void schedulesTheFirstFailureAtTheInitialRetryDelayAndSanitizesTheError() {
        OutboxEvent event = event();
        Instant beforePublishing = Instant.now();
        when(repository.lockUnpublishedBatch(10)).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker\nunavailable"));
        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload().toString()))
                .thenReturn(failed);

        assertThat(service().publishBatch()).isZero();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("IllegalStateException: broker unavailable");
        assertThat(event.getNextAttemptAt())
                .isBetween(beforePublishing.plusSeconds(5), Instant.now().plusSeconds(5));
    }

    private OutboxPublisherService service() {
        return new OutboxPublisherService(
                repository,
                kafkaTemplate,
                new OutboxPublisherProperties(
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(1)
                )
        );
    }

    private OutboxEvent event() {
        UUID orderId = UUID.randomUUID();
        EventEnvelope<InventoryReservedV1> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                InventoryEventFactory.INVENTORY_RESERVED,
                1,
                Instant.parse("2026-01-01T00:00:00Z"),
                "inventory-outbox-test",
                orderId,
                new InventoryReservedV1(orderId, UUID.randomUUID())
        );
        return OutboxEvent.create(
                envelope,
                "InventoryReservation",
                InventoryEventFactory.INVENTORY_EVENTS_TOPIC,
                orderId.toString(),
                new ObjectMapper().findAndRegisterModules().valueToTree(envelope)
        );
    }
}
