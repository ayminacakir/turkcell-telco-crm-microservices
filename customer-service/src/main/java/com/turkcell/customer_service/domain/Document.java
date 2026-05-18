package com.turkcell.customer_service.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    private String fileRef;

    private LocalDateTime verifiedAt;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
}