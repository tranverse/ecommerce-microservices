package com.example.ecommerce.notification.provider;

import com.example.ecommerce.notification.domain.NotificationChannel;
import com.example.ecommerce.notification.domain.NotificationType;

import java.util.UUID;

public record NotificationDelivery(
        UUID notificationId,
        UUID customerId,
        NotificationType type,
        NotificationChannel channel,
        String content,
        String correlationId
) {
}
