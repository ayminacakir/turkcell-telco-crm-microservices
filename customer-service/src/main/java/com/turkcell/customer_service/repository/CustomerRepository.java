package com.turkcell.customer_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turkcell.customer_service.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByIdentityNumberAndDeletedFalse(String identityNumber);

    Optional<Customer> findByIdAndDeletedFalse(UUID id);

    Customer save(Customer customer);
}
