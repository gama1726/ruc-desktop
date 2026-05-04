package ru.ruc.desktop.service;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ruc.desktop.domain.AgentStatus;
import ru.ruc.desktop.domain.ManagedAgent;
import ru.ruc.desktop.repository.MachineRepository;
import ru.ruc.desktop.repository.ManagedAgentRepository;
import ru.ruc.desktop.web.dto.AgentHeartbeatRequest;
import ru.ruc.desktop.web.dto.AgentHeartbeatResponse;

@Service
public class AgentService {

    private final ManagedAgentRepository agentRepository;
    private final MachineRepository machineRepository;

    public AgentService(ManagedAgentRepository agentRepository, MachineRepository machineRepository) {
        this.agentRepository = agentRepository;
        this.machineRepository = machineRepository;
    }

    @Transactional
    public AgentHeartbeatResponse heartbeat(AgentHeartbeatRequest req) {
        ManagedAgent agent = agentRepository.findByAgentUid(req.agentUid()).orElseGet(ManagedAgent::new);
        agent.setAgentUid(req.agentUid().trim());
        agent.setDisplayName(req.displayName());
        agent.setRemoteId(normalize(req.remoteId()));
        agent.setIpAddress(req.ipAddress());
        agent.setLastSeenAt(Instant.now());
        agent.setStatus(AgentStatus.ONLINE);

        if (req.machineId() != null) {
            var machine = machineRepository
                    .findById(req.machineId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "machine not found"));
            agent.setMachine(machine);
        }

        agent = agentRepository.save(agent);
        Long machineId = agent.getMachine() != null ? agent.getMachine().getId() : null;
        return new AgentHeartbeatResponse(
                agent.getId(),
                agent.getAgentUid(),
                agent.getStatus().name(),
                machineId,
                agent.getRemoteId(),
                agent.getLastSeenAt());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replaceAll("\\s+", "").trim();
        return v.isBlank() ? null : v;
    }
}
