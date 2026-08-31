package com.example.ecommerce.payment.repository;

import com.example.ecommerce.payment.messaging.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    boolean existsByAggregateIdAndEventTypeIn(UUID aggregateId, Collection<String> eventTypes);

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, occurred_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);
}
