package com.example.ecommerce.notification.service;

import com.example.ecommerce.notification.domain.CustomerNotification;
import com.example.ecommerce.notification.domain.NotificationStatus;
import com.example.ecommerce.notification.provider.NotificationDelivery;
import com.example.ecommerce.notification.repository.CustomerNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationDeliveryTransactionService {

    private final CustomerNotificationRepository notificationRepository;

    public NotificationDeliveryTransactionService(CustomerNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationDeliveryAttempt start(UUID notificationId) {
        CustomerNotification notification = lock(notificationId);
        if (notification.getStatus() == NotificationStatus.SENT) {
            return NotificationDeliveryAttempt.skip();
        }
        notification.startAttempt(Instant.now());
        return NotificationDeliveryAttempt.send(new NotificationDelivery(
                notification.getId(),
                notification.getCustomerId(),
                notification.getType(),
                notification.getChannel(),
                notification.getContent(),
                notification.getCorrelationId()
        ));
    }

    @Transactional
    public void complete(UUID notificationId) {
        lock(notificationId).markSent(Instant.now());
    }

    @Transactional
    public void fail(UUID notificationId, String failureReason) {
        lock(notificationId).markFailed(safeFailureReason(failureReason), Instant.now());
    }

    private CustomerNotification lock(UUID notificationId) {
        return notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification disappeared during delivery: " + notificationId));
    }

    private String safeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Notification provider failed without a reason";
        }
        return failureReason.length() <= 500
                ? failureReason
                : failureReason.substring(0, 500);
    }
}
