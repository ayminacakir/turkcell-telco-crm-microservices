package com.turkcell.billing_service.service.impl;

import com.turkcell.billing_service.domain.entity.BillCycle;
import com.turkcell.billing_service.dto.request.CreateBillCycleRequest;
import com.turkcell.billing_service.dto.response.BillCycleResponse;
import com.turkcell.billing_service.repository.BillCycleRepository;
import com.turkcell.billing_service.service.BillCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillCycleServiceImpl implements BillCycleService {

    private final BillCycleRepository billCycleRepository;

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

    private LocalDate calculateNextRunDate(int dayOfMonth) {
        LocalDate now = LocalDate.now();
        LocalDate candidate = now.withDayOfMonth(dayOfMonth);
        return candidate.isBefore(now) || candidate.isEqual(now)
                ? candidate.plusMonths(1)
                : candidate;
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