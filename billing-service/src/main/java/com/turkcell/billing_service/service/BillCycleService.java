package com.turkcell.billing_service.service;

import com.turkcell.billing_service.dto.request.CreateBillCycleRequest;
import com.turkcell.billing_service.dto.response.BillCycleResponse;

import java.util.List;
import java.util.UUID;

public interface BillCycleService {
    BillCycleResponse create(CreateBillCycleRequest request);
    List<BillCycleResponse> getByCustomerId(UUID customerId);
    BillCycleResponse getById(UUID id);
    void delete(UUID id);
}