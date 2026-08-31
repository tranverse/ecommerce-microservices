package com.example.ecommerce.notification.repository;

import com.example.ecommerce.notification.messaging.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
