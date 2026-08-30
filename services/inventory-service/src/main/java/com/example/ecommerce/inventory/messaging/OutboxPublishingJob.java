package com.example.ecommerce.inventory.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublishingJob {

    private final OutboxPublisherService publisherService;

    public OutboxPublishingJob(OutboxPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:PT1S}")
    public void publish() {
        publisherService.publishBatch();
    }
}
