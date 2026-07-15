package com.turkcell.customer_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.customer_service.dto.request.CreateCustomerRequest;
import com.turkcell.customer_service.dto.response.CustomerResponse;
import com.turkcell.customer_service.entity.Customer;
import com.turkcell.customer_service.enums.CustomerStatus;
import com.turkcell.customer_service.enums.CustomerType;
import com.turkcell.customer_service.excepiton.CustomerNotFoundException;
import com.turkcell.customer_service.outbox.repository.OutboxEventRepository;
import com.turkcell.customer_service.repository.AddressRepository;
import com.turkcell.customer_service.repository.ContactInfoRepository;
import com.turkcell.customer_service.repository.CustomerRepository;
import com.turkcell.customer_service.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ContactInfoRepository contactInfoRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private AuditLogService auditLogService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                addressRepository,
                documentRepository,
                contactInfoRepository,
                outboxEventRepository,
                new ObjectMapper().findAndRegisterModules(),
                auditLogService);
    }

    @Test
    void create_withValidIndividualCustomer_savesAndPublishesOutboxEvent() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                CustomerType.INDIVIDUAL, "Ali", "Yılmaz", null, "12345678901", null);

        Customer saved = buildCustomer(UUID.randomUUID(), CustomerStatus.PENDING);
        when(customerRepository.existsByIdentityNumberAndDeletedFalse("12345678901")).thenReturn(false);
        when(customerRepository.save(any())).thenReturn(saved);

        CustomerResponse response = customerService.create(request);

        assertThat(response.type()).isEqualTo(CustomerType.INDIVIDUAL);
        verify(customerRepository).save(any(Customer.class));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void create_withDuplicateIdentityNumber_throwsIllegalArgument() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                CustomerType.INDIVIDUAL, "Ali", "Yılmaz", null, "12345678901", null);

        when(customerRepository.existsByIdentityNumberAndDeletedFalse("12345678901")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void create_withCorporateCustomer_requiresVkn() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                CustomerType.CORPORATE, null, null, "Turkcell A.Ş.", "1234567890", null);

        Customer saved = buildCustomer(UUID.randomUUID(), CustomerStatus.PENDING);
        when(customerRepository.existsByIdentityNumberAndDeletedFalse("1234567890")).thenReturn(false);
        when(customerRepository.save(any())).thenReturn(saved);

        CustomerResponse response = customerService.create(request);

        assertThat(response).isNotNull();
    }

    @Test
    void getById_withExistingId_returnsCustomerResponse() {
        UUID id = UUID.randomUUID();
        Customer customer = buildCustomer(id, CustomerStatus.ACTIVE);
        when(customerRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void getById_withNonexistentId_throwsCustomerNotFoundException() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void approveKyc_withPendingCustomer_setsActiveAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Customer customer = buildCustomer(id, CustomerStatus.PENDING);
        when(customerRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.approveKyc(id);

        assertThat(response.status()).isEqualTo(CustomerStatus.ACTIVE);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void rejectKyc_withPendingCustomer_setsRejectedAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Customer customer = buildCustomer(id, CustomerStatus.PENDING);
        when(customerRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.rejectKyc(id);

        assertThat(response.status()).isEqualTo(CustomerStatus.REJECTED);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void approveKyc_withNonexistentCustomer_throwsCustomerNotFoundException() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.approveKyc(id))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(outboxEventRepository, never()).save(any());
    }

    private Customer buildCustomer(UUID id, CustomerStatus status) {
        Customer c = new Customer();
        try {
            java.lang.reflect.Field idField = Customer.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        c.setType(CustomerType.INDIVIDUAL);
        c.setFirstName("Ali");
        c.setLastName("Yılmaz");
        c.setIdentityNumber("12345678901");
        c.prePersist(); // sets createdAt + defaults status to PENDING
        c.setStatus(status); // override after prePersist
        return c;
    }
}
