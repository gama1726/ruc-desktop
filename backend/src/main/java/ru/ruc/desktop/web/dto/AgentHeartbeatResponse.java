package ru.ruc.desktop.web.dto;

import java.time.Instant;

public record AgentHeartbeatResponse(
        Long id,
        String agentUid,
        String status,
        Long machineId,
        String remoteId,
        Instant lastSeenAt) {}
