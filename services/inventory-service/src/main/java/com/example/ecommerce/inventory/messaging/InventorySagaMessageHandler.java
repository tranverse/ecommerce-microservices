package com.example.ecommerce.inventory.messaging;

import com.example.ecommerce.inventory.exception.InsufficientInventoryException;
import com.example.ecommerce.inventory.exception.InventoryItemNotFoundException;
import com.example.ecommerce.inventory.exception.ReservationConflictException;
import org.springframework.stereotype.Service;

@Service
public class InventorySagaMessageHandler {

    private final InventorySagaTransactionService transactionService;

    public InventorySagaMessageHandler(InventorySagaTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public SagaProcessingResult handle(InventoryCommand command) {
        if (command instanceof ReservationRequestedCommand reservationCommand) {
            return reserve(reservationCommand);
        }
        if (command instanceof ReleaseRequestedCommand releaseCommand) {
            return transactionService.releaseAndRecord(releaseCommand);
        }
        if (command instanceof ConfirmationRequestedCommand confirmationCommand) {
            return transactionService.confirmAndRecord(confirmationCommand);
        }
        throw new InvalidEventException("Unsupported inventory command");
    }

    private SagaProcessingResult reserve(ReservationRequestedCommand command) {
        try {
            return transactionService.reserveAndRecordSuccess(command);
        } catch (InsufficientInventoryException exception) {
            return transactionService.recordReservationFailure(
                    command, InventoryReservationFailureReason.INSUFFICIENT_INVENTORY);
        } catch (InventoryItemNotFoundException exception) {
            return transactionService.recordReservationFailure(
                    command, InventoryReservationFailureReason.ITEM_NOT_FOUND);
        } catch (ReservationConflictException exception) {
            return transactionService.recordReservationFailure(
                    command, InventoryReservationFailureReason.REQUEST_CONFLICT);
        }
    }
}
