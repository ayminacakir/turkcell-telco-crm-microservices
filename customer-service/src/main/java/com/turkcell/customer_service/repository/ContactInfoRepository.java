package com.turkcell.customer_service.repository;

import com.turkcell.customer_service.entity.ContactInfo;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactInfoRepository extends JpaRepository<ContactInfo, UUID> {

    List<ContactInfo> findByCustomerId(UUID customerId);
}