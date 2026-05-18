package com.turkcell.subscription_service.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import com.turkcell.subscription_service.domain.enums.SubscriptionStatus;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * customerId başka mikroservisten gelir.
     * Bu yüzden Customer tablosuna FK vermiyoruz.
     */
    @Column(nullable = false)
    private Long customerId;

    /*
     * msisdn = abonenin telefon numarası
     */
    @Column(nullable = false, unique = true)
    private String msisdn;

    /*
     * tariffCode product-catalog-service içindeki tarife kodunu temsil eder.
     * Bu yüzden Tariff tablosuna FK vermiyoruz.
     */
    @Column(nullable = false)
    private String tariffCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private LocalDateTime activatedAt;

    private LocalDateTime terminatedAt;
}