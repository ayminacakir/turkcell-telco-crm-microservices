package com.turkcell.order_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID productId;

    @Column(length = 150)
    private String productName;

    @Column(length = 100)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private com.turkcell.order_service.enums.OrderProductType productType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column
    private Integer minutesIncluded;

    @Column
    private Integer smsIncluded;

    @Column
    private Integer dataMbIncluded;

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public com.turkcell.order_service.enums.OrderProductType getProductType() {
        return productType;
    }

    public void setProductType(com.turkcell.order_service.enums.OrderProductType productType) {
        this.productType = productType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Integer getMinutesIncluded() {
        return minutesIncluded;
    }

    public void setMinutesIncluded(Integer minutesIncluded) {
        this.minutesIncluded = minutesIncluded;
    }

    public Integer getSmsIncluded() {
        return smsIncluded;
    }

    public void setSmsIncluded(Integer smsIncluded) {
        this.smsIncluded = smsIncluded;
    }

    public Integer getDataMbIncluded() {
        return dataMbIncluded;
    }

    public void setDataMbIncluded(Integer dataMbIncluded) {
        this.dataMbIncluded = dataMbIncluded;
    }
}