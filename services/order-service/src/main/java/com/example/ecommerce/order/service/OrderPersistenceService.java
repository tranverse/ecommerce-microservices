package com.example.ecommerce.order.service;

import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.ProductSnapshot;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.OrderSummaryResponse;
import com.example.ecommerce.order.dto.PageResponse;
import com.example.ecommerce.order.exception.IdempotencyKeyConflictException;
import com.example.ecommerce.order.exception.OrderNotFoundException;
import com.example.ecommerce.order.mapper.OrderMapper;
import com.example.ecommerce.order.repository.CustomerOrderRepository;
import com.example.ecommerce.order.messaging.OrderEventFactory;
import com.example.ecommerce.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceService.class);

    private final CustomerOrderRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final OrderEventFactory eventFactory;
    private final OrderMapper mapper;

    public OrderPersistenceService(
            CustomerOrderRepository repository,
            OutboxEventRepository outboxRepository,
            OrderEventFactory eventFactory,
            OrderMapper mapper
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.eventFactory = eventFactory;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findIdempotentOrder(
            UUID customerId,
            String idempotencyKey,
            String requestHash
    ) {
        return repository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(order -> matchingResponse(order, requestHash));
    }

    @Transactional
    public OrderResponse createOrder(
            UUID customerId,
            String idempotencyKey,
            String requestHash,
            List<ProductSnapshot> snapshots
    ) {
        Optional<CustomerOrder> existing = repository.findByCustomerIdAndIdempotencyKey(
                customerId,
                idempotencyKey
        );
        if (existing.isPresent()) {
            return matchingResponse(existing.get(), requestHash);
        }

        CustomerOrder saved = repository.saveAndFlush(
                CustomerOrder.create(customerId, idempotencyKey, requestHash, snapshots)
        );
        outboxRepository.saveAndFlush(eventFactory.inventoryReservationRequested(saved));
        log.info(
                "Created order orderId={} customerId={} itemCount={} totalAmount={} currency={}",
                saved.getId(),
                customerId,
                saved.getItems().size(),
                saved.getTotalAmount(),
                saved.getCurrency()
        );
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID customerId, UUID orderId) {
        return repository.findByIdAndCustomerId(orderId, customerId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(UUID customerId, int page, int size) {
        Page<CustomerOrder> orders = repository.findAllByCustomerId(
                customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.from(orders, mapper::toSummary);
    }

    private OrderResponse matchingResponse(CustomerOrder order, String requestHash) {
        if (!order.matchesRequestHash(requestHash)) {
            throw new IdempotencyKeyConflictException();
        }
        return mapper.toResponse(order);
    }
}
