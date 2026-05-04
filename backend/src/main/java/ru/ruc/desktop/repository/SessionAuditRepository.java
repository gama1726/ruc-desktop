package ru.ruc.desktop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ruc.desktop.domain.SessionAudit;

public interface SessionAuditRepository extends JpaRepository<SessionAudit, Long> {

    List<SessionAudit> findByOperatorUserIdOrderByCreatedAtDesc(String operatorUserId);
}
