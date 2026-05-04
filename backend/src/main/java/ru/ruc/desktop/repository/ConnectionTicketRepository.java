package ru.ruc.desktop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ruc.desktop.domain.ConnectionTicket;
import ru.ruc.desktop.domain.ConnectionTicketStatus;

public interface ConnectionTicketRepository extends JpaRepository<ConnectionTicket, Long> {

    Optional<ConnectionTicket> findByToken(String token);

    List<ConnectionTicket> findByOperatorUserIdAndStatusOrderByCreatedAtDesc(
            String operatorUserId, ConnectionTicketStatus status);

    Optional<ConnectionTicket> findTopByRemoteIdAndStatusOrderByCreatedAtDesc(
            String remoteId, ConnectionTicketStatus status);

    List<ConnectionTicket> findByStatusAndExpiresAtBefore(ConnectionTicketStatus status, Instant now);
}
