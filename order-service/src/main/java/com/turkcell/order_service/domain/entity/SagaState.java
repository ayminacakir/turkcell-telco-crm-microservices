package com.turkcell.order_service.domain.entity;

import java.time.LocalDateTime;

import com.turkcell.order_service.domain.enums.SagaStep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "saga_states")
public class SagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStep currentStep;
    @Column(columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime lastUpdated;

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setCurrentStep(SagaStep currentStep) {
        this.currentStep = currentStep;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}