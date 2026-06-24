package com.turkcell.customer_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.convert.ReadingConverter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.turkcell.customer_service.dto.request.CreateAddressRequest;
import com.turkcell.customer_service.dto.request.CreateCustomerRequest;
import com.turkcell.customer_service.dto.request.CreateDocumentRequest;
import com.turkcell.customer_service.dto.request.UpdateCustomerRequest;
import com.turkcell.customer_service.dto.response.CustomerResponse;
import com.turkcell.customer_service.entity.Address;
import com.turkcell.customer_service.entity.Document;
import com.turkcell.customer_service.service.CustomerService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return customerService.getById(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {

        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        customerService.softDelete(id);
    }

    @PostMapping("/{id}/kyc/approve")
    public CustomerResponse approveKyc(@PathVariable UUID id) {
        return customerService.approveKyc(id);
    }

    @PostMapping("/{id}/kyc/reject")
    public CustomerResponse rejectKyc(@PathVariable UUID id) {
        return customerService.rejectKyc(id);
    }

    @PostMapping("/{id}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public Address addAddress(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAddressRequest request) {
        return customerService.addAddress(id, request);
    }

    @GetMapping("/{id}/addresses")
    public List<Address> getAddresses(@PathVariable UUID id) {
        return customerService.getAddresses(id);
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public Document addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDocumentRequest request) {
        return customerService.addDocument(id, request);
    }

    @GetMapping("/{id}/documents")
    public List<Document> getDocuments(@PathVariable UUID id) {
        return customerService.getDocuments(id);
    }

}
