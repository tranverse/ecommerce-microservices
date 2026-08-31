package com.example.ecommerce.notification.service;

import com.example.ecommerce.notification.domain.CustomerNotification;
import com.example.ecommerce.notification.domain.NotificationChannel;
import com.example.ecommerce.notification.messaging.ConflictingNotificationEventException;
import com.example.ecommerce.notification.messaging.OrderLifecycleEvent;
import com.example.ecommerce.notification.messaging.ProcessedEvent;
import com.example.ecommerce.notification.repository.CustomerNotificationRepository;
import com.example.ecommerce.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
public class NotificationAcceptanceService {

    public static final String CONSUMER_NAME = "notification-order-lifecycle-v1";

    private static final Logger log = LoggerFactory.getLogger(NotificationAcceptanceService.class);

    private final CustomerNotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationAcceptanceService(
            CustomerNotificationRepository notificationRepository,
            ProcessedEventRepository processedEventRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public NotificationAcceptanceResult accept(OrderLifecycleEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            CustomerNotification existing = notificationRepository
                    .findBySourceEventIdAndChannel(event.eventId(), NotificationChannel.SYSTEM)
                    .or(() -> notificationRepository.findByOrderIdAndChannel(
                            event.orderId(), NotificationChannel.SYSTEM))
                    .orElseThrow(() -> new IllegalStateException(
                            "Processed event has no matching notification: " + event.eventId()));
            if (!matches(existing, event)) {
                throw new ConflictingNotificationEventException(event.orderId());
            }
            log.info("Ignored duplicate order event eventId={} orderId={}",
                    event.eventId(), event.orderId());
            return new NotificationAcceptanceResult(
                    existing.getId(), NotificationAcceptanceOutcome.EXACT_DUPLICATE);
        }

        CustomerNotification existing = notificationRepository
                .findByOrderIdAndChannel(event.orderId(), NotificationChannel.SYSTEM)
                .orElse(null);
        if (existing != null) {
            if (!matches(existing, event)) {
                throw new ConflictingNotificationEventException(event.orderId());
            }
            processedEventRepository.save(ProcessedEvent.create(event, CONSUMER_NAME, Instant.now()));
            log.info("Ignored semantic duplicate order event eventId={} orderId={}",
                    event.eventId(), event.orderId());
            return new NotificationAcceptanceResult(
                    existing.getId(), NotificationAcceptanceOutcome.SEMANTIC_DUPLICATE);
        }

        Instant now = Instant.now();
        CustomerNotification notification = notificationRepository.save(CustomerNotification.pending(
                event.eventId(),
                event.orderId(),
                event.customerId(),
                event.notificationType(),
                event.content(),
                event.cancellationReason(),
                event.correlationId(),
                now
        ));
        processedEventRepository.save(ProcessedEvent.create(event, CONSUMER_NAME, now));
        log.info("Accepted order notification eventId={} notificationId={} orderId={} type={}",
                event.eventId(), notification.getId(), event.orderId(), event.notificationType());
        return new NotificationAcceptanceResult(
                notification.getId(), NotificationAcceptanceOutcome.ACCEPTED);
    }

    private boolean matches(CustomerNotification notification, OrderLifecycleEvent event) {
        return notification.getOrderId().equals(event.orderId())
                && notification.getCustomerId().equals(event.customerId())
                && notification.getType() == event.notificationType()
                && Objects.equals(
                        notification.getCancellationReason(), event.cancellationReason());
    }
}
