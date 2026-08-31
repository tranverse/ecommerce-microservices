package com.example.ecommerce.payment.repository;

import com.example.ecommerce.payment.messaging.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
