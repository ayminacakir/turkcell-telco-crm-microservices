package com.turkcell.customer_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.customer_service.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByCustomerId(UUID customerId);

    Document save(Document document);
}
