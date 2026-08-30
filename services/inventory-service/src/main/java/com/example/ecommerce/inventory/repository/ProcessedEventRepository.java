package com.example.ecommerce.inventory.repository;

import com.example.ecommerce.inventory.messaging.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
