package ru.ruc.desktop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ruc.desktop.domain.RemoteAccessSession;
import ru.ruc.desktop.domain.SessionStatus;

public interface RemoteAccessSessionRepository extends JpaRepository<RemoteAccessSession, Long> {

    List<RemoteAccessSession> findByStatusOrderByStartedAtDesc(SessionStatus status);

    List<RemoteAccessSession> findByOperatorUserIdAndStatusOrderByStartedAtDesc(
            String operatorUserId, SessionStatus status);
}
