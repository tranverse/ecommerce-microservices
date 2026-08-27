package com.example.ecommerce.inventory.service;

import com.example.ecommerce.inventory.domain.InventoryItem;
import com.example.ecommerce.inventory.domain.InventoryReservation;
import com.example.ecommerce.inventory.domain.ReservationStatus;
import com.example.ecommerce.inventory.dto.CreateReservationRequest;
import com.example.ecommerce.inventory.dto.InventoryResponse;
import com.example.ecommerce.inventory.dto.ReservationLineRequest;
import com.example.ecommerce.inventory.dto.ReservationResponse;
import com.example.ecommerce.inventory.exception.DuplicateReservationProductException;
import com.example.ecommerce.inventory.exception.InsufficientInventoryException;
import com.example.ecommerce.inventory.exception.InvalidReservationStateException;
import com.example.ecommerce.inventory.exception.InvalidStockQuantityException;
import com.example.ecommerce.inventory.exception.InventoryItemNotFoundException;
import com.example.ecommerce.inventory.exception.ReservationConflictException;
import com.example.ecommerce.inventory.exception.ReservationNotFoundException;
import com.example.ecommerce.inventory.mapper.InventoryMapper;
import com.example.ecommerce.inventory.repository.InventoryItemRepository;
import com.example.ecommerce.inventory.repository.InventoryReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            InventoryReservationRepository reservationRepository
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public InventoryResponse setStock(UUID productId, int totalQuantity) {
        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> InventoryItem.create(productId, totalQuantity));
        try {
            item.setTotalQuantity(totalQuantity);
        } catch (IllegalArgumentException exception) {
            throw new InvalidStockQuantityException(exception.getMessage());
        }
        InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
        log.info("Set inventory productId={} totalQuantity={} reservedQuantity={}",
                productId, saved.getTotalQuantity(), saved.getReservedQuantity());
        return InventoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getStock(UUID productId) {
        return inventoryItemRepository.findById(productId)
                .map(InventoryMapper::toResponse)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));
    }

    @Transactional
    public ReservationOperationResult reserve(CreateReservationRequest request) {
        Map<UUID, Integer> quantities = toUniqueQuantities(request.items());
        var existing = reservationRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return existingResult(request.orderId(), quantities, existing.get());
        }

        List<InventoryItem> lockedItems = lockInventory(quantities.keySet().stream().toList());

        existing = reservationRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return existingResult(request.orderId(), quantities, existing.get());
        }

        Map<UUID, InventoryItem> inventoryByProduct = lockedItems.stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, item -> item));
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            InventoryItem item = inventoryByProduct.get(entry.getKey());
            int requested = entry.getValue();
            if (requested > item.getAvailableQuantity()) {
                throw new InsufficientInventoryException(
                        item.getProductId(), requested, item.getAvailableQuantity());
            }
        }
        quantities.forEach((productId, quantity) -> inventoryByProduct.get(productId).reserve(quantity));

        InventoryReservation reservation = InventoryReservation.create(request.orderId(), quantities);
        InventoryReservation saved = reservationRepository.saveAndFlush(reservation);
        log.info("Reserved inventory reservationId={} orderId={} itemCount={}",
                saved.getId(), saved.getOrderId(), saved.getItems().size());
        return new ReservationOperationResult(InventoryMapper.toResponse(saved), true);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID orderId) {
        return InventoryMapper.toResponse(findReservation(orderId));
    }

    @Transactional
    public ReservationResponse release(UUID orderId) {
        return finishReservation(orderId, ReservationStatus.RELEASED, InventoryItem::release);
    }

    @Transactional
    public ReservationResponse confirm(UUID orderId) {
        return finishReservation(orderId, ReservationStatus.CONFIRMED, InventoryItem::confirm);
    }

    private ReservationResponse finishReservation(
            UUID orderId,
            ReservationStatus targetStatus,
            InventoryMutation mutation
    ) {
        InventoryReservation reservation = findReservation(orderId);
        if (reservation.getStatus() == targetStatus) {
            return InventoryMapper.toResponse(reservation);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new InvalidReservationStateException(reservation.getStatus(), targetStatus.name().toLowerCase());
        }

        List<UUID> productIds = reservation.getItems().stream()
                .map(item -> item.getProductId())
                .toList();
        Map<UUID, InventoryItem> inventoryByProduct = lockInventory(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, item -> item));
        reservation.getItems().forEach(item -> mutation.accept(
                inventoryByProduct.get(item.getProductId()), item.getQuantity()));

        if (targetStatus == ReservationStatus.RELEASED) {
            reservation.release();
        } else {
            reservation.confirm();
        }
        InventoryReservation saved = reservationRepository.saveAndFlush(reservation);
        log.info("Changed inventory reservation orderId={} status={}", orderId, targetStatus);
        return InventoryMapper.toResponse(saved);
    }

    private InventoryReservation findReservation(UUID orderId) {
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ReservationNotFoundException(orderId));
    }

    private ReservationOperationResult existingResult(
            UUID orderId,
            Map<UUID, Integer> quantities,
            InventoryReservation existing
    ) {
        if (!existing.matches(quantities)) {
            throw new ReservationConflictException(orderId);
        }
        return new ReservationOperationResult(InventoryMapper.toResponse(existing), false);
    }

    private List<InventoryItem> lockInventory(List<UUID> productIds) {
        List<UUID> orderedProductIds = new ArrayList<>(productIds);
        orderedProductIds.sort(Comparator.naturalOrder());
        List<InventoryItem> lockedItems = inventoryItemRepository
                .findAllByProductIdInForUpdate(orderedProductIds);
        if (lockedItems.size() != orderedProductIds.size()) {
            var existingIds = lockedItems.stream().map(InventoryItem::getProductId).collect(Collectors.toSet());
            UUID missing = orderedProductIds.stream()
                    .filter(productId -> !existingIds.contains(productId))
                    .findFirst()
                    .orElseThrow();
            throw new InventoryItemNotFoundException(missing);
        }
        return lockedItems;
    }

    private Map<UUID, Integer> toUniqueQuantities(List<ReservationLineRequest> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (ReservationLineRequest item : items) {
            Integer previous = quantities.putIfAbsent(item.productId(), item.quantity());
            if (previous != null) {
                throw new DuplicateReservationProductException(item.productId());
            }
        }
        return Map.copyOf(quantities);
    }

    @FunctionalInterface
    private interface InventoryMutation {
        void accept(InventoryItem item, int quantity);
    }
}
