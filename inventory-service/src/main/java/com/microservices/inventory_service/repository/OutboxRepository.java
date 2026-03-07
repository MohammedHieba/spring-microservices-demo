package com.microservices.inventory_service.repository;

import com.microservices.inventory_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
}