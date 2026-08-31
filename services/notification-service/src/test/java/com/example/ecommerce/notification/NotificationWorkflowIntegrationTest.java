package com.example.ecommerce.notification;

import com.example.ecommerce.notification.domain.NotificationStatus;
import com.example.ecommerce.notification.messaging.ConflictingNotificationEventException;
import com.example.ecommerce.notification.messaging.OrderCancellationReasonV1;
import com.example.ecommerce.notification.messaging.OrderCancelledEvent;
import com.example.ecommerce.notification.messaging.OrderConfirmedEvent;
import com.example.ecommerce.notification.messaging.OrderLifecycleEventHandler;
import com.example.ecommerce.notification.messaging.OrderLifecycleEventParser;
import com.example.ecommerce.notification.provider.NotificationDeliveryException;
import com.example.ecommerce.notification.provider.NotificationSender;
import com.example.ecommerce.notification.repository.CustomerNotificationRepository;
import com.example.ecommerce.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@SpringBootTest(properties = "notification.messaging.listener-enabled=false")
@Import(TestcontainersConfiguration.class)
class NotificationWorkflowIntegrationTest {

    @Autowired
    private OrderLifecycleEventHandler handler;

    @Autowired
    private CustomerNotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private NotificationSender sender;

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void deliversConfirmedNotificationAndSuppressesExactDuplicate() {
        OrderConfirmedEvent event = confirmed(UUID.randomUUID(), UUID.randomUUID());

        handler.handle(event);
        handler.handle(event);

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getAttempts()).isEqualTo(1);
            assertThat(notification.getSentAt()).isNotNull();
        });
        assertThat(processedEventRepository.count()).isEqualTo(1);
        verify(sender, times(1)).send(any());
        verifyNoMoreInteractions(sender);
    }

    @Test
    void recordsSemanticDuplicateWithoutSendingTwice() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        handler.handle(confirmed(UUID.randomUUID(), orderId, customerId));
        OrderConfirmedEvent semanticDuplicate = confirmed(UUID.randomUUID(), orderId, customerId);
        handler.handle(semanticDuplicate);
        handler.handle(semanticDuplicate);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(2);
        verify(sender, times(1)).send(any());
        verifyNoMoreInteractions(sender);
    }

    @Test
    void persistsCancellationReason() {
        UUID orderId = UUID.randomUUID();
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(), OrderLifecycleEventParser.ORDER_CANCELLED, Instant.now(),
                "notification-cancelled-flow", orderId, UUID.randomUUID(),
                OrderCancellationReasonV1.PAYMENT_FAILED);

        handler.handle(event);

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getCancellationReason()).isEqualTo("PAYMENT_FAILED");
            assertThat(notification.getContent()).contains("PAYMENT_FAILED");
        });
    }

    @Test
    void recordsProviderFailureWithoutRollingBackAcceptedEvent() {
        doThrow(new NotificationDeliveryException("provider unavailable"))
                .when(sender).send(any());
        OrderConfirmedEvent event = confirmed(UUID.randomUUID(), UUID.randomUUID());

        handler.handle(event);

        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.getAttempts()).isEqualTo(1);
            assertThat(notification.getFailureReason()).isEqualTo("provider unavailable");
        });
        assertThat(processedEventRepository.existsById(event.eventId())).isTrue();
    }

    @Test
    void rejectsContradictoryTerminalEvents() {
        UUID orderId = UUID.randomUUID();
        handler.handle(confirmed(UUID.randomUUID(), orderId));
        OrderCancelledEvent contradictory = new OrderCancelledEvent(
                UUID.randomUUID(), OrderLifecycleEventParser.ORDER_CANCELLED, Instant.now(),
                "notification-conflict-flow", orderId, UUID.randomUUID(),
                OrderCancellationReasonV1.SYSTEM_ERROR);

        assertThatThrownBy(() -> handler.handle(contradictory))
                .isInstanceOf(ConflictingNotificationEventException.class);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsSemanticDuplicateWithDifferentCustomer() {
        UUID orderId = UUID.randomUUID();
        handler.handle(confirmed(UUID.randomUUID(), orderId));
        OrderConfirmedEvent corrupted = confirmed(UUID.randomUUID(), orderId);

        assertThatThrownBy(() -> handler.handle(new OrderConfirmedEvent(
                corrupted.eventId(), corrupted.eventType(), corrupted.occurredAt(),
                corrupted.correlationId(), corrupted.orderId(), UUID.randomUUID())))
                .isInstanceOf(ConflictingNotificationEventException.class);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    private OrderConfirmedEvent confirmed(UUID eventId, UUID orderId) {
        return confirmed(eventId, orderId, UUID.randomUUID());
    }

    private OrderConfirmedEvent confirmed(UUID eventId, UUID orderId, UUID customerId) {
        return new OrderConfirmedEvent(
                eventId, OrderLifecycleEventParser.ORDER_CONFIRMED, Instant.now(),
                "notification-confirmed-flow", orderId, customerId);
    }
}
