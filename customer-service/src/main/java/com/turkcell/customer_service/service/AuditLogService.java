package com.turkcell.customer_service.service;

import com.turkcell.customer_service.entity.AuditLog;
import com.turkcell.customer_service.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {

    private static final String CUSTOMER = "CUSTOMER";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logCustomerAction(UUID customerId, String action, String details) {
        AuditLog log = new AuditLog();
        log.setEntityType(CUSTOMER);
        log.setEntityId(customerId);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
