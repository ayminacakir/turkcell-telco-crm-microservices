package com.turkcell.subscription_service.service;

import com.turkcell.subscription_service.entity.AuditLog;
import com.turkcell.subscription_service.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {

    private static final String SUBSCRIPTION = "SUBSCRIPTION";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logSubscriptionAction(UUID subscriptionId, String action, String details) {
        AuditLog log = new AuditLog();
        log.setEntityType(SUBSCRIPTION);
        log.setEntityId(subscriptionId);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
