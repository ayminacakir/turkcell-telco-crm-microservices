package com.turkcell.payment_service.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "payment_service")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Siparis odemelerinde (order.created akisi) fatura henuz olmadigi icin nullable.
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "external_ref", length = 200)
    private String externalRef;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_request_id", unique = true)
    private UUID paymentRequestId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAttempt> attempts = new ArrayList<>();

    public Payment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public UUID getPaymentRequestId() { return paymentRequestId; }
    public void setPaymentRequestId(UUID paymentRequestId) { this.paymentRequestId = paymentRequestId; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public LocalDateTime getFirstFailedAt() { return firstFailedAt; }
    public void setFirstFailedAt(LocalDateTime firstFailedAt) { this.firstFailedAt = firstFailedAt; }

    public List<PaymentAttempt> getAttempts() { return attempts; }
    public void setAttempts(List<PaymentAttempt> attempts) { this.attempts = attempts; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
