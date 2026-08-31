package com.example.ecommerce.notification.service;

import java.util.UUID;

public record NotificationAcceptanceResult(
        UUID notificationId,
        NotificationAcceptanceOutcome outcome
) {
}
