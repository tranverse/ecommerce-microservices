package com.example.ecommerce.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CustomerNotificationTest {

    @Test
    void movesThroughSuccessfulDeliveryStates() {
        Instant createdAt = Instant.parse("2026-08-31T10:00:00Z");
        CustomerNotification notification = confirmed(createdAt);

        notification.startAttempt(createdAt.plusSeconds(1));
        notification.markSent(createdAt.plusSeconds(2));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getSentAt()).isEqualTo(createdAt.plusSeconds(2));
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void failedDeliveryCanBeRetried() {
        Instant createdAt = Instant.parse("2026-08-31T10:00:00Z");
        CustomerNotification notification = confirmed(createdAt);

        notification.startAttempt(createdAt.plusSeconds(1));
        notification.markFailed("provider unavailable", createdAt.plusSeconds(2));
        notification.startAttempt(createdAt.plusSeconds(3));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(notification.getAttempts()).isEqualTo(2);
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void sentNotificationCannotStartAnotherAttempt() {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        CustomerNotification notification = confirmed(now);
        notification.startAttempt(now.plusSeconds(1));
        notification.markSent(now.plusSeconds(2));

        assertThatIllegalStateException()
                .isThrownBy(() -> notification.startAttempt(now.plusSeconds(3)));
    }

    @Test
    void cancellationReasonMustMatchNotificationType() {
        assertThatIllegalArgumentException().isThrownBy(() -> CustomerNotification.pending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CONFIRMED, "confirmed", "PAYMENT_FAILED",
                "notification-domain-test", Instant.now()));
    }

    private CustomerNotification confirmed(Instant now) {
        return CustomerNotification.pending(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.ORDER_CONFIRMED, "Order confirmed", null,
                "notification-domain-test", now);
    }
}
