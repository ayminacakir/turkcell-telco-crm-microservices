package com.turkcell.customer_service.repository;

import com.turkcell.customer_service.entity.Address;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByCustomerId(UUID customerId);

    Address save(Address address);
}
