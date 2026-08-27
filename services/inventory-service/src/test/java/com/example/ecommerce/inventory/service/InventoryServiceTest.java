package com.example.ecommerce.inventory.service;

import com.example.ecommerce.inventory.domain.InventoryItem;
import com.example.ecommerce.inventory.domain.InventoryReservation;
import com.example.ecommerce.inventory.dto.CreateReservationRequest;
import com.example.ecommerce.inventory.dto.ReservationLineRequest;
import com.example.ecommerce.inventory.exception.InsufficientInventoryException;
import com.example.ecommerce.inventory.exception.InvalidStockQuantityException;
import com.example.ecommerce.inventory.exception.ReservationConflictException;
import com.example.ecommerce.inventory.repository.InventoryItemRepository;
import com.example.ecommerce.inventory.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryItemRepository, reservationRepository);
    }

    @Test
    void rejectsAStockReductionBelowTheAlreadyReservedQuantity() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(productId, 5);
        item.reserve(3);
        when(inventoryItemRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.setStock(productId, 2))
                .isInstanceOf(InvalidStockQuantityException.class);
        verify(inventoryItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservesAllRequestedStockInOneUseCase() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(productId, 5);
        CreateReservationRequest request = request(orderId, productId, 2);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(productId)))
                .thenReturn(List.of(item));
        when(reservationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationOperationResult result = inventoryService.reserve(request);

        assertThat(result.created()).isTrue();
        assertThat(item.getReservedQuantity()).isEqualTo(2);
        verify(reservationRepository).saveAndFlush(any(InventoryReservation.class));
    }

    @Test
    void rejectsTheWholeReservationWhenOneItemIsInsufficient() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = InventoryItem.create(productId, 1);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(productId)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> inventoryService.reserve(request(orderId, productId, 2)))
                .isInstanceOf(InsufficientInventoryException.class);
        assertThat(item.getReservedQuantity()).isZero();
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsTheExistingReservationForAnIdenticalRetry() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryReservation existing = InventoryReservation.create(orderId, Map.of(productId, 2));
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        ReservationOperationResult result = inventoryService.reserve(request(orderId, productId, 2));

        assertThat(result.created()).isFalse();
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void rejectsReusingAnOrderIdWithDifferentReservationData() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryReservation existing = InventoryReservation.create(orderId, Map.of(productId, 1));
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> inventoryService.reserve(request(orderId, productId, 2)))
                .isInstanceOf(ReservationConflictException.class);
    }

    private CreateReservationRequest request(UUID orderId, UUID productId, int quantity) {
        return new CreateReservationRequest(
                orderId, List.of(new ReservationLineRequest(productId, quantity)));
    }
}
