package com.example.ecommerce.notification.service;

import com.example.ecommerce.notification.provider.NotificationDeliveryException;
import com.example.ecommerce.notification.provider.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationDeliveryTransactionService transactionService;
    private final NotificationSender sender;

    public NotificationDeliveryService(
            NotificationDeliveryTransactionService transactionService,
            NotificationSender sender
    ) {
        this.transactionService = transactionService;
        this.sender = sender;
    }

    public void deliver(UUID notificationId) {
        NotificationDeliveryAttempt attempt = transactionService.start(notificationId);
        if (!attempt.sendRequired()) {
            log.info("Skipped notification delivery notificationId={} because it is already sent",
                    notificationId);
            return;
        }
        try {
            sender.send(attempt.delivery());
            transactionService.complete(notificationId);
            log.info("Delivered notification notificationId={}", notificationId);
        } catch (NotificationDeliveryException exception) {
            transactionService.fail(notificationId, exception.getMessage());
            log.warn("Notification provider rejected delivery notificationId={} reason={}",
                    notificationId, exception.getMessage());
        }
    }
}
