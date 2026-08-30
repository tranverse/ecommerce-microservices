package com.example.ecommerce.inventory.messaging;

import com.example.ecommerce.inventory.dto.CreateReservationRequest;
import com.example.ecommerce.inventory.dto.ReservationLineRequest;
import com.example.ecommerce.inventory.repository.OutboxEventRepository;
import com.example.ecommerce.inventory.repository.ProcessedEventRepository;
import com.example.ecommerce.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class InventorySagaTransactionService {

    public static final String CONSUMER_NAME = "inventory-saga-v1";

    private static final Logger log = LoggerFactory.getLogger(InventorySagaTransactionService.class);

    private final InventoryService inventoryService;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryEventFactory eventFactory;

    public InventorySagaTransactionService(
            InventoryService inventoryService,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            InventoryEventFactory eventFactory
    ) {
        this.inventoryService = inventoryService;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventFactory = eventFactory;
    }

    @Transactional
    public SagaProcessingResult reserveAndRecordSuccess(ReservationRequestedCommand command) {
        if (alreadyProcessed(command)) {
            return SagaProcessingResult.ALREADY_PROCESSED;
        }

        CreateReservationRequest request = new CreateReservationRequest(
                command.orderId(),
                command.items().stream()
                        .map(item -> new ReservationLineRequest(item.productId(), item.quantity()))
                        .toList()
        );
        var result = inventoryService.reserve(request);

        outboxEventRepository.save(eventFactory.reservationSucceeded(command, result.reservation().id()));
        recordProcessed(command);
        log.info(
                "Processed inventory reservation command eventId={} orderId={} reservationId={} created={}",
                command.eventId(), command.orderId(), result.reservation().id(), result.created());
        return SagaProcessingResult.APPLIED;
    }

    @Transactional
    public SagaProcessingResult recordReservationFailure(
            ReservationRequestedCommand command,
            InventoryReservationFailureReason reason
    ) {
        if (alreadyProcessed(command)) {
            return SagaProcessingResult.ALREADY_PROCESSED;
        }
        outboxEventRepository.save(eventFactory.reservationFailed(command, reason));
        recordProcessed(command);
        log.info(
                "Recorded inventory reservation failure eventId={} orderId={} reason={}",
                command.eventId(), command.orderId(), reason);
        return SagaProcessingResult.APPLIED;
    }

    @Transactional
    public SagaProcessingResult releaseAndRecord(ReleaseRequestedCommand command) {
        if (alreadyProcessed(command)) {
            return SagaProcessingResult.ALREADY_PROCESSED;
        }
        inventoryService.release(command.orderId());
        recordProcessed(command);
        log.info("Processed inventory release command eventId={} orderId={}",
                command.eventId(), command.orderId());
        return SagaProcessingResult.APPLIED;
    }

    @Transactional
    public SagaProcessingResult confirmAndRecord(ConfirmationRequestedCommand command) {
        if (alreadyProcessed(command)) {
            return SagaProcessingResult.ALREADY_PROCESSED;
        }
        inventoryService.confirm(command.orderId());
        recordProcessed(command);
        log.info("Processed inventory confirmation command eventId={} orderId={}",
                command.eventId(), command.orderId());
        return SagaProcessingResult.APPLIED;
    }

    private boolean alreadyProcessed(InventoryCommand command) {
        boolean processed = processedEventRepository.existsById(command.eventId());
        if (processed) {
            log.info("Ignored duplicate inventory command eventId={} eventType={} orderId={}",
                    command.eventId(), command.eventType(), command.orderId());
        }
        return processed;
    }

    private void recordProcessed(InventoryCommand command) {
        processedEventRepository.save(ProcessedEvent.create(command, CONSUMER_NAME, Instant.now()));
    }
}
