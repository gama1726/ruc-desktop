package ru.ruc.desktop.web;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ruc.desktop.service.AgentService;
import ru.ruc.desktop.service.SessionAuditService;
import ru.ruc.desktop.web.dto.AgentHeartbeatRequest;
import ru.ruc.desktop.web.dto.AgentHeartbeatResponse;

@RestController
@RequestMapping("/api/agents")
@Validated
public class AgentController {

    private final AgentService agentService;
    private final SessionAuditService auditService;

    public AgentController(AgentService agentService, SessionAuditService auditService) {
        this.agentService = agentService;
        this.auditService = auditService;
    }

    @PostMapping("/heartbeat")
    public AgentHeartbeatResponse heartbeat(@Valid @RequestBody AgentHeartbeatRequest req) {
        AgentHeartbeatResponse response = agentService.heartbeat(req);
        auditService.log("agent", response.machineId(), response.remoteId(), "AGENT_HEARTBEAT", response.agentUid());
        return response;
    }
}
