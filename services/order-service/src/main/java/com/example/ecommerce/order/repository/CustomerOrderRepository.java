package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.domain.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<CustomerOrder> findAllByCustomerId(UUID customerId, Pageable pageable);
}
