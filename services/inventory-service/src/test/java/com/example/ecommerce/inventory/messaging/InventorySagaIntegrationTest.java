package com.example.ecommerce.inventory.messaging;

import com.example.ecommerce.inventory.TestcontainersConfiguration;
import com.example.ecommerce.inventory.domain.ReservationStatus;
import com.example.ecommerce.inventory.repository.InventoryItemRepository;
import com.example.ecommerce.inventory.repository.InventoryReservationRepository;
import com.example.ecommerce.inventory.repository.OutboxEventRepository;
import com.example.ecommerce.inventory.repository.ProcessedEventRepository;
import com.example.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventorySagaIntegrationTest {

    @Autowired
    private InventorySagaMessageHandler handler;

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
    void atomicallyReservesStockRecordsInboxAndCreatesOneOutcomeForDuplicateDelivery() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 5);
        ReservationRequestedCommand command = reservationCommand(orderId, productId, 2);

        assertThat(handler.handle(command)).isEqualTo(SagaProcessingResult.APPLIED);
        assertThat(handler.handle(command)).isEqualTo(SagaProcessingResult.ALREADY_PROCESSED);

        assertThat(inventoryService.getStock(productId).reservedQuantity()).isEqualTo(2);
        assertThat(reservationRepository.findByOrderId(orderId)).get()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo(InventoryEventFactory.INVENTORY_RESERVED);
            assertThat(outbox.getAggregateId()).isEqualTo(orderId);
            assertThat(outbox.getPayload().path("payload").path("reservationId").asText()).isNotBlank();
        });
    }

    @Test
    void rollsBackPartialReservationAndRecordsABusinessFailureOutcome() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 1);

        assertThat(handler.handle(reservationCommand(orderId, productId, 2)))
                .isEqualTo(SagaProcessingResult.APPLIED);

        assertThat(inventoryService.getStock(productId).reservedQuantity()).isZero();
        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType())
                    .isEqualTo(InventoryEventFactory.INVENTORY_RESERVATION_FAILED);
            assertThat(outbox.getPayload().path("payload").path("reason").asText())
                    .isEqualTo(InventoryReservationFailureReason.INSUFFICIENT_INVENTORY.name());
        });
    }

    @Test
    void appliesReleaseCommandsIdempotentlyUsingTheirEventId() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 3);
        handler.handle(reservationCommand(orderId, productId, 2));
        ReleaseRequestedCommand release = new ReleaseRequestedCommand(
                UUID.randomUUID(),
                InventoryCommandParser.RELEASE_REQUESTED,
                Instant.now(),
                "inventory-release-test",
                orderId
        );

        assertThat(handler.handle(release)).isEqualTo(SagaProcessingResult.APPLIED);
        assertThat(handler.handle(release)).isEqualTo(SagaProcessingResult.ALREADY_PROCESSED);

        assertThat(inventoryService.getStock(productId).reservedQuantity()).isZero();
        assertThat(reservationRepository.findByOrderId(orderId)).get()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }

    @Test
    void confirmsReservedStockAndRecordsTheCommandInTheInbox() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 3);
        handler.handle(reservationCommand(orderId, productId, 2));
        ConfirmationRequestedCommand confirmation = new ConfirmationRequestedCommand(
                UUID.randomUUID(),
                InventoryCommandParser.CONFIRMATION_REQUESTED,
                Instant.now(),
                "inventory-confirmation-test",
                orderId
        );

        assertThat(handler.handle(confirmation)).isEqualTo(SagaProcessingResult.APPLIED);

        assertThat(inventoryService.getStock(productId)).satisfies(stock -> {
            assertThat(stock.totalQuantity()).isEqualTo(1);
            assertThat(stock.reservedQuantity()).isZero();
            assertThat(stock.availableQuantity()).isEqualTo(1);
        });
        assertThat(reservationRepository.findByOrderId(orderId)).get()
                .extracting(reservation -> reservation.getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }

    private ReservationRequestedCommand reservationCommand(UUID orderId, UUID productId, int quantity) {
        return new ReservationRequestedCommand(
                UUID.randomUUID(),
                InventoryCommandParser.RESERVATION_REQUESTED,
                Instant.now(),
                "inventory-saga-test",
                orderId,
                List.of(new InventoryReservationRequestedV1.Item(productId, quantity))
        );
    }
}
