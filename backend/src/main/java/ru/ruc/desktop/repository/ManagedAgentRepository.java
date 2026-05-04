package ru.ruc.desktop.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ruc.desktop.domain.ManagedAgent;

public interface ManagedAgentRepository extends JpaRepository<ManagedAgent, Long> {

    Optional<ManagedAgent> findByAgentUid(String agentUid);
}
