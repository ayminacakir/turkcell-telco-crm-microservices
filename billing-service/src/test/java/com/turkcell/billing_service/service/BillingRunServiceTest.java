package com.turkcell.billing_service.service;

import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.outbox.service.OutboxEventWriter;
import com.turkcell.billing_service.repository.BillCycleRepository;
import com.turkcell.billing_service.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingRunServiceTest {

    @Mock
    private BillCycleRepository billCycleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private BillingRunService billingRunService;

    @Captor
    private ArgumentCaptor<Invoice> invoiceCaptor;

    private UUID customerId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
    }

    private BillCycle dueBillCycle() {
        BillCycle cycle = new BillCycle();
        cycle.setCustomerId(customerId);
        cycle.setSubscriptionId(subscriptionId);
        cycle.setDayOfMonth(LocalDate.now().getDayOfMonth());
        cycle.setNextRunDate(LocalDate.now());
        return cycle;
    }

    // --- Happy path ---

    @Test
    void runBilling_shouldCreateInvoiceAndPublishInvoiceGenerated_whenCycleIsDue() {
        BillCycle cycle = dueBillCycle();
        when(billCycleRepository.findAll()).thenReturn(List.of(cycle));
        when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        billingRunService.runBilling();

        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice saved = invoiceCaptor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getGrandTotal()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(outboxEventWriter).write(any(), eq("Invoice"), eq("InvoiceGenerated"), any());
        assertThat(cycle.getNextRunDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void runBilling_shouldSkipCycle_whenNotDueYet() {
        BillCycle cycle = dueBillCycle();
        cycle.setNextRunDate(LocalDate.now().plusDays(5));
        when(billCycleRepository.findAll()).thenReturn(List.of(cycle));

        billingRunService.runBilling();

        verify(invoiceRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
    }

    // --- Idempotency ---

    @Test
    void runBilling_shouldNotCreateDuplicateInvoice_whenPeriodAlreadyInvoiced() {
        BillCycle cycle = dueBillCycle();
        Invoice existing = new Invoice();
        existing.setPeriodStart(LocalDate.now().minusMonths(1));
        existing.setPeriodEnd(LocalDate.now());
        when(billCycleRepository.findAll()).thenReturn(List.of(cycle));
        when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of(existing));

        billingRunService.runBilling();

        verify(invoiceRepository, never()).save(any());
        verify(outboxEventWriter, never()).write(any(), anyString(), anyString(), any());
    }
}
