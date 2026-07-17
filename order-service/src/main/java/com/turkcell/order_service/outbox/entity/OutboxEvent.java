package com.turkcell.order_service.outbox.entity;

import com.turkcell.order_service.outbox.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    // Kac kez publish denendigi; MAX_RETRIES asilinca event kalici FAILED kalir.
    @Column(nullable = false)
    private int retryCount = 0;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
