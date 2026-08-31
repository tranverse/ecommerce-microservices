package com.example.ecommerce.notification.messaging;

import com.example.ecommerce.notification.service.NotificationAcceptanceResult;
import com.example.ecommerce.notification.service.NotificationAcceptanceService;
import com.example.ecommerce.notification.service.NotificationDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class OrderLifecycleEventHandler {

    private final NotificationAcceptanceService acceptanceService;
    private final NotificationDeliveryService deliveryService;

    public OrderLifecycleEventHandler(
            NotificationAcceptanceService acceptanceService,
            NotificationDeliveryService deliveryService
    ) {
        this.acceptanceService = acceptanceService;
        this.deliveryService = deliveryService;
    }

    public void handle(OrderLifecycleEvent event) {
        NotificationAcceptanceResult accepted = acceptanceService.accept(event);
        deliveryService.deliver(accepted.notificationId());
    }
}
