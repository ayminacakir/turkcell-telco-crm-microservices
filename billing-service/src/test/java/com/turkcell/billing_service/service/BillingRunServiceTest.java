package com.turkcell.billing_service.service;

import com.turkcell.billing_service.config.BillingProperties;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
// BeforeEach'teki ortak stub'lar her test metodunda kullanilmiyor;
// Mockito strict-stubs bunu hata sayip CI'yi kiriyordu (UnnecessaryStubbing).
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingRunServiceTest {

    @Mock
    private BillCycleRepository billCycleRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UsageAggregationService usageAggregationService;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private BillingProperties billingProperties;

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
        cycle.setMonthlyFee(new BigDecimal("149.90"));
        cycle.setDayOfMonth(LocalDate.now().getDayOfMonth());
        cycle.setNextRunDate(LocalDate.now());
        return cycle;
    }

    @Test
    void runBilling_shouldCreateInvoiceAndPublishInvoiceGenerated_whenCycleIsDue() {
        when(billingProperties.resolveMonthlyFee(any())).thenReturn(new BigDecimal("149.90"));
        when(billingProperties.getTaxRate()).thenReturn(new BigDecimal("0.20"));
        when(billingProperties.getOverage()).thenReturn(new BillingProperties.Overage());

        BillCycle cycle = dueBillCycle();
        when(billCycleRepository.findAll()).thenReturn(List.of(cycle));
        when(invoiceRepository.findByCustomerId(customerId)).thenReturn(List.of());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usageAggregationService.findForPeriod(any(), any(), any())).thenReturn(null);

        billingRunService.runBilling();

        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice saved = invoiceCaptor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(saved.getStatus()).isEqualTo("ISSUED");
        assertThat(saved.getGrandTotal()).isEqualByComparingTo("179.88");

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
