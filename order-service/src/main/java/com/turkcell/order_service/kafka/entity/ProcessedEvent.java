package com.turkcell.order_service.kafka.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (this.processedAt == null) {
            this.processedAt = LocalDateTime.now();
        }
    }
}
