package ru.ruc.desktop.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentHeartbeatRequest(
        @NotBlank String agentUid,
        String displayName,
        Long machineId,
        String remoteId,
        String ipAddress) {}
