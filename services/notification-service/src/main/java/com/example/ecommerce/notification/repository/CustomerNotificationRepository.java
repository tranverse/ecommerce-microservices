package com.example.ecommerce.notification.repository;

import com.example.ecommerce.notification.domain.CustomerNotification;
import com.example.ecommerce.notification.domain.NotificationChannel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID> {

    Optional<CustomerNotification> findBySourceEventIdAndChannel(
            UUID sourceEventId,
            NotificationChannel channel
    );

    Optional<CustomerNotification> findByOrderIdAndChannel(
            UUID orderId,
            NotificationChannel channel
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from CustomerNotification notification where notification.id = :id")
    Optional<CustomerNotification> findByIdForUpdate(@Param("id") UUID id);
}
