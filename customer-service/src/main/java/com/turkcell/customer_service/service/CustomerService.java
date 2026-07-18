package com.turkcell.customer_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.customer_service.dto.request.CreateAddressRequest;
import com.turkcell.customer_service.dto.request.CreateContactInfoRequest;
import com.turkcell.customer_service.dto.request.CreateCustomerRequest;
import com.turkcell.customer_service.dto.request.CreateDocumentRequest;
import com.turkcell.customer_service.dto.request.UpdateCustomerRequest;
import com.turkcell.customer_service.dto.response.AddressResponse;
import com.turkcell.customer_service.dto.response.ContactInfoResponse;
import com.turkcell.customer_service.dto.response.CustomerResponse;
import com.turkcell.customer_service.dto.response.DocumentResponse;
import com.turkcell.customer_service.entity.Address;
import com.turkcell.customer_service.entity.ContactInfo;
import com.turkcell.customer_service.entity.Customer;
import com.turkcell.customer_service.entity.Document;
import com.turkcell.customer_service.enums.ContactType;
import com.turkcell.customer_service.enums.CustomerStatus;
import com.turkcell.customer_service.enums.CustomerType;
import com.turkcell.customer_service.excepiton.CustomerNotFoundException;
import com.turkcell.customer_service.outbox.entity.OutboxEvent;
import com.turkcell.customer_service.outbox.enums.OutboxStatus;
import com.turkcell.customer_service.outbox.event.CustomerKYCApprovedEvent;
import com.turkcell.customer_service.outbox.event.CustomerKYCRejectedEvent;
import com.turkcell.customer_service.outbox.event.CustomerRegisteredEvent;
import com.turkcell.customer_service.outbox.event.CustomerUpdatedEvent;
import com.turkcell.customer_service.outbox.repository.OutboxEventRepository;
import com.turkcell.customer_service.repository.AddressRepository;
import com.turkcell.customer_service.repository.ContactInfoRepository;
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
    private final ContactInfoRepository contactInfoRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public CustomerService(
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            DocumentRepository documentRepository,
            ContactInfoRepository contactInfoRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.documentRepository = documentRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
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

        saveOutboxEvent(savedCustomer.getId(), "CustomerRegistered",
                new CustomerRegisteredEvent(
                        UUID.randomUUID(), "CustomerRegistered",
                        savedCustomer.getId(), savedCustomer.getType(),
                        savedCustomer.getFirstName(), savedCustomer.getLastName(),
                        savedCustomer.getCompanyName(), savedCustomer.getIdentityNumber(),
                        LocalDateTime.now()));

        auditLogService.logCustomerAction(savedCustomer.getId(), "CUSTOMER_CREATED",
                "type=" + savedCustomer.getType() + " identityNumber=" + savedCustomer.getIdentityNumber());

        return toResponse(savedCustomer);
    }

    public CustomerResponse getById(UUID id) {
        Customer customer = findCustomerById(id);
        return toResponse(customer);
    }

    // Operasyon konsolu: silinmemis tum musteriler.
    public List<CustomerResponse> getAll() {
        return customerRepository.findByDeletedFalse().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = findCustomerById(id);

        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setCompanyName(request.companyName());
        customer.setDateOfBirth(request.dateOfBirth());

        Customer saved = customerRepository.save(customer);

        saveOutboxEvent(saved.getId(), "CustomerUpdated",
                new CustomerUpdatedEvent(
                        UUID.randomUUID(), "CustomerUpdated",
                        saved.getId(), saved.getFirstName(), saved.getLastName(),
                        saved.getCompanyName(), LocalDateTime.now()));

        auditLogService.logCustomerAction(saved.getId(), "CUSTOMER_UPDATED",
                "firstName=" + saved.getFirstName() + " lastName=" + saved.getLastName());

        return toResponse(saved);
    }

    @Transactional
    public void softDelete(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setDeleted(true);
        customer.setStatus(CustomerStatus.DELETED);
        customer.setDeletedAt(LocalDateTime.now());

        customerRepository.save(customer);

        auditLogService.logCustomerAction(customer.getId(), "CUSTOMER_DELETED", null);
    }

    @Transactional
    public CustomerResponse approveKyc(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.ACTIVE);

        Customer saved = customerRepository.save(customer);

        saveOutboxEvent(saved.getId(), "CustomerKYCApproved",
                new CustomerKYCApprovedEvent(
                        UUID.randomUUID(), "CustomerKYCApproved",
                        saved.getId(), LocalDateTime.now()));

        auditLogService.logCustomerAction(saved.getId(), "CUSTOMER_KYC_APPROVED", null);

        return toResponse(saved);
    }

    @Transactional
    public CustomerResponse rejectKyc(UUID id) {
        Customer customer = findCustomerById(id);

        customer.setStatus(CustomerStatus.REJECTED);

        Customer saved = customerRepository.save(customer);

        saveOutboxEvent(saved.getId(), "CustomerKYCRejected",
                new CustomerKYCRejectedEvent(
                        UUID.randomUUID(), "CustomerKYCRejected",
                        saved.getId(), LocalDateTime.now()));

        auditLogService.logCustomerAction(saved.getId(), "CUSTOMER_KYC_REJECTED", null);

        return toResponse(saved);
    }

    @Transactional
    public AddressResponse addAddress(UUID customerId, CreateAddressRequest request) {
        findCustomerById(customerId);

        Address address = new Address();
        address.setCustomerId(customerId);
        address.setLine1(request.line1());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setPostalCode(request.postalCode());
        address.setDefaultAddress(request.defaultAddress());

        Address savedAddress = addressRepository.save(address);

        return toAddressResponse(savedAddress);
    }

    public List<AddressResponse> getAddresses(UUID customerId) {
        findCustomerById(customerId);

        return addressRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public DocumentResponse addDocument(UUID customerId, CreateDocumentRequest request) {
        findCustomerById(customerId);

        Document document = new Document();
        document.setCustomerId(customerId);
        document.setType(request.type());
        document.setFileRef(request.fileRef());

        Document savedDocument = documentRepository.save(document);

        return toDocumentResponse(savedDocument);
    }

    public List<DocumentResponse> getDocuments(UUID customerId) {
        findCustomerById(customerId);

        return documentRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private Customer findCustomerById(UUID id) {
        return customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer with id " + id + " not found."));
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

    private void saveOutboxEvent(UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType("CUSTOMER");
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(json);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
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

    private AddressResponse toAddressResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCustomerId(),
                address.getLine1(),
                address.getCity(),
                address.getDistrict(),
                address.getPostalCode(),
                address.isDefaultAddress());
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getCustomerId(),
                document.getType(),
                document.getFileRef(),
                document.getVerifiedAt());
    }

    @Transactional
    public ContactInfoResponse addContactInfo(
            UUID customerId,
            CreateContactInfoRequest request) {
        findCustomerById(customerId);
        validateContactInfo(request);

        ContactInfo contactInfo = new ContactInfo();
        contactInfo.setCustomerId(customerId);
        contactInfo.setType(request.type());
        contactInfo.setValue(request.value());
        contactInfo.setPrimaryContact(request.primaryContact());

        ContactInfo savedContactInfo = contactInfoRepository.save(contactInfo);

        return toContactInfoResponse(savedContactInfo);
    }

    public List<ContactInfoResponse> getContactInfos(UUID customerId) {
        findCustomerById(customerId);

        return contactInfoRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toContactInfoResponse)
                .toList();
    }

    private void validateContactInfo(CreateContactInfoRequest request) {
        if (request.type() == ContactType.EMAIL) {
            if (!request.value().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                throw new IllegalArgumentException("Invalid email address.");
            }
        }

        if (request.type() == ContactType.PHONE) {
            if (!request.value().matches("\\d{10,15}")) {
                throw new IllegalArgumentException("Invalid phone number.");
            }
        }
    }

    private ContactInfoResponse toContactInfoResponse(ContactInfo contactInfo) {
        return new ContactInfoResponse(
                contactInfo.getId(),
                contactInfo.getCustomerId(),
                contactInfo.getType(),
                contactInfo.getValue(),
                contactInfo.isPrimaryContact());
    }
}
