package com.microservices.order_service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;
    @Column(name = "aggregate_type")
    private String aggregateType;
    @Column(name = "aggregate_id")
    private String aggregateId;
    @Column(name = "topic")
    private String topic;
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "json")
    private JsonNode payload;
    @Column(name = "created_at")
    private Instant createdAt;
}