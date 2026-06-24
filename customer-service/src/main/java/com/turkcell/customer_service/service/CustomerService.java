package com.turkcell.customer_service.service;

import com.turkcell.customer_service.dto.request.CreateAddressRequest;
import com.turkcell.customer_service.dto.request.CreateCustomerRequest;
import com.turkcell.customer_service.dto.request.CreateDocumentRequest;
import com.turkcell.customer_service.dto.request.UpdateCustomerRequest;
import com.turkcell.customer_service.dto.response.CustomerResponse;
import com.turkcell.customer_service.entity.Address;
import com.turkcell.customer_service.entity.Customer;
import com.turkcell.customer_service.entity.Document;
import com.turkcell.customer_service.enums.CustomerStatus;
import com.turkcell.customer_service.enums.CustomerType;
import com.turkcell.customer_service.repository.AddressRepository;
import com.turkcell.customer_service.repository.CustomerRepository;
import com.turkcell.customer_service.repository.DocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final DocumentRepository documentRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            DocumentRepository documentRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        validateCustomerRequest(request);

        if (customerRepository.existsByIdentityNumberAndDeletedFalse(request.identityNumber())) {
            throw new IllegalArgumentException("This identity number is already registered.");
        }

        Customer customer = new Customer();
        customer.setType(request.type());
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setCompanyName(request.companyName());
        customer.setIdentityNumber(request.identityNumber());
        customer.setDateOfBirth(request.dateOfBirth());

        Customer savedCustomer = customerRepository.save(customer);

        return toResponse(savedCustomer);
    }

    public CustomerResponse getById(UUID id) {
        Customer customer = findCustomerById(id);
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = findCustomerById(id);

        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setCompanyName(request.companyName());
        customer.setDateOfBirth(request.dateOfBirth());

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void softDelete(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setDeleted(true);
        customer.setStatus(CustomerStatus.DELETED);
        customer.setDeletedAt(LocalDateTime.now());

        customerRepository.save(customer);
    }

    @Transactional
    public CustomerResponse approveKyc(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.ACTIVE);

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse rejectKyc(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.REJECTED);

        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public Address addAddress(UUID customerId, CreateAddressRequest request) {
        findCustomerById(customerId);

        Address address = new Address();
        address.setCustomerId(customerId);
        address.setLine1(request.line1());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setPostalCode(request.postalCode());
        address.setDefaultAddress(request.defaultAddress());

        return addressRepository.save(address);
    }

    public List<Address> getAddresses(UUID customerId) {
        findCustomerById(customerId);
        return addressRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Document addDocument(UUID customerId, CreateDocumentRequest request) {
        findCustomerById(customerId);

        Document document = new Document();
        document.setCustomerId(customerId);
        document.setType(request.type());
        document.setFileRef(request.fileRef());

        return documentRepository.save(document);
    }

    public List<Document> getDocuments(UUID customerId) {
        findCustomerById(customerId);
        return documentRepository.findByCustomerId(customerId);
    }

    private Customer findCustomerById(UUID id) {
        return customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    private void validateCustomerRequest(CreateCustomerRequest request) {
        if (request.type() == CustomerType.INDIVIDUAL) {
            if (request.firstName() == null || request.firstName().isBlank()) {
                throw new IllegalArgumentException("First name is required for individual customer.");
            }

            if (request.lastName() == null || request.lastName().isBlank()) {
                throw new IllegalArgumentException("Last name is required for individual customer.");
            }

            if (!isValidTckn(request.identityNumber())) {
                throw new IllegalArgumentException("Invalid TCKN.");
            }
        }

        if (request.type() == CustomerType.CORPORATE) {
            if (request.companyName() == null || request.companyName().isBlank()) {
                throw new IllegalArgumentException("Company name is required for corporate customer.");
            }

            if (!isValidVkn(request.identityNumber())) {
                throw new IllegalArgumentException("Invalid VKN.");
            }
        }
    }

    private boolean isValidTckn(String identityNumber) {
        return identityNumber != null && identityNumber.matches("\\d{11}"); // Sadece rakamlardan oluşsun ve 11 haneli
                                                                            // olsun.
    }

    private boolean isValidVkn(String identityNumber) {
        return identityNumber != null && identityNumber.matches("\\d{10}");
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getType(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getCompanyName(),
                customer.getIdentityNumber(),
                customer.getDateOfBirth(),
                customer.getStatus(),
                customer.getCreatedAt());
    }
}