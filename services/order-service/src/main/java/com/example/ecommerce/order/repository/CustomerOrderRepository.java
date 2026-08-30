package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.domain.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<CustomerOrder> findAllByCustomerId(UUID customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :orderId")
    Optional<CustomerOrder> findByIdForUpdate(@Param("orderId") UUID orderId);
}
