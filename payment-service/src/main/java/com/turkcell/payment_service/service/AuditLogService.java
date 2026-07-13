package com.turkcell.payment_service.service;

import com.turkcell.payment_service.domain.entity.AuditLog;
import com.turkcell.payment_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Odeme islemlerinin audit_log tablosuna kaydedilmesi (dokuman bolum 13).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String PAYMENT_ENTITY_TYPE = "PAYMENT";

    private final AuditLogRepository auditLogRepository;

    public void logPaymentAction(UUID paymentId, String action, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEntityType(PAYMENT_ENTITY_TYPE);
        auditLog.setEntityId(paymentId);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }
}
