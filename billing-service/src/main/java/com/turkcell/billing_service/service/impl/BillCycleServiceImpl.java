package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.config.BillingProperties;
import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.dto.request.CreateBillCycleRequest;
import com.turkcell.billing_service.dto.response.BillCycleResponse;
import com.turkcell.billing_service.event.SubscriptionActivatedEvent;
import com.turkcell.billing_service.kafka.entity.ProcessedEvent;
import com.turkcell.billing_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.billing_service.repository.BillCycleRepository;
import com.turkcell.billing_service.service.BillCycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillCycleServiceImpl implements BillCycleService {

    private final BillCycleRepository billCycleRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final BillingProperties billingProperties;

    @Override
    public BillCycleResponse create(CreateBillCycleRequest request) {
        BillCycle cycle = new BillCycle();
        cycle.setCustomerId(request.customerId());
        cycle.setDayOfMonth(request.dayOfMonth());
        cycle.setNextRunDate(calculateNextRunDate(request.dayOfMonth()));
        BillCycle saved = billCycleRepository.save(cycle);
        return toResponse(saved);
    }

    @Override
    public List<BillCycleResponse> getByCustomerId(UUID customerId) {
        return billCycleRepository.findByCustomerId(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public BillCycleResponse getById(UUID id) {
        BillCycle cycle = billCycleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("BillCycle not found: " + id));
        return toResponse(cycle);
    }

    @Override
    public void delete(UUID id) {
        billCycleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void createBillCycleForSubscription(SubscriptionActivatedEvent event) {
        if (event.eventId() != null && processedEventRepository.existsById(event.eventId())) {
            log.warn("SubscriptionActivated event {} already processed, skipping", event.eventId());
            return;
        }

        if (billCycleRepository.existsByCustomerId(event.customerId())) {
            log.warn("BillCycle already exists for customerId: {}, skipping", event.customerId());
        } else {
            LocalDate activationDate = event.activatedAt().toLocalDate();
            int dayOfMonth = activationDate.getDayOfMonth();
            LocalDate nextRunDate = calculateNextRunDateFromActivation(activationDate, dayOfMonth);

            BillCycle billCycle = new BillCycle();
            billCycle.setCustomerId(event.customerId());
            billCycle.setSubscriptionId(event.subscriptionId());
            billCycle.setTariffCode(event.tariffCode());
            billCycle.setMonthlyFee(billingProperties.resolveMonthlyFee(event.tariffCode()));
            billCycle.setDayOfMonth(dayOfMonth);
            billCycle.setNextRunDate(nextRunDate);
            billCycleRepository.save(billCycle);

            log.info("BillCycle created for customerId: {}, subscriptionId: {}, nextRunDate: {}",
                    event.customerId(), event.subscriptionId(), nextRunDate);
        }

        if (event.eventId() != null) {
            processedEventRepository.save(new ProcessedEvent(event.eventId(), "SubscriptionActivated"));
        }
    }

    private LocalDate calculateNextRunDate(int dayOfMonth) {
        LocalDate now = LocalDate.now();
        int safeDay = Math.min(dayOfMonth, now.lengthOfMonth());
        LocalDate candidate = now.withDayOfMonth(safeDay);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusMonths(1);
            candidate = candidate.withDayOfMonth(Math.min(dayOfMonth, candidate.lengthOfMonth()));
        }
        return candidate;
    }

    private LocalDate calculateNextRunDateFromActivation(LocalDate activationDate, int dayOfMonth) {
        LocalDate nextRunDate = activationDate.plusMonths(1);
        int lastDayOfNextMonth = nextRunDate.lengthOfMonth();
        return dayOfMonth > lastDayOfNextMonth
                ? nextRunDate.withDayOfMonth(lastDayOfNextMonth)
                : nextRunDate.withDayOfMonth(dayOfMonth);
    }

    private BillCycleResponse toResponse(BillCycle cycle) {
        return new BillCycleResponse(
                cycle.getId(),
                cycle.getCustomerId(),
                cycle.getDayOfMonth(),
                cycle.getNextRunDate()
        );
    }
}