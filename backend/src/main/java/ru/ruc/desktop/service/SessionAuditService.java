package ru.ruc.desktop.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ruc.desktop.domain.SessionAudit;
import ru.ruc.desktop.repository.SessionAuditRepository;

@Service
public class SessionAuditService {

    private final SessionAuditRepository auditRepository;

    public SessionAuditService(SessionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void log(String operatorUserId, Long machineId, String remoteId, String action, String details) {
        SessionAudit a = new SessionAudit();
        a.setOperatorUserId(operatorUserId == null || operatorUserId.isBlank() ? "system" : operatorUserId);
        a.setMachineId(machineId);
        a.setRemoteId(remoteId);
        a.setAction(action);
        a.setDetails(details);
        a.setCreatedAt(Instant.now());
        auditRepository.save(a);
    }
}
