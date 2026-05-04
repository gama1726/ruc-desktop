package ru.ruc.desktop.web.dto;

import java.time.Instant;

public record SessionResponse(
        Long id,
        Long machineId,
        String roomCode,
        String hostname,
        String operatorUserId,
        Instant startedAt,
        Instant endedAt,
        String status,
        String connectionHint,
        String deepLink) {

    public static SessionResponse from(
            ru.ruc.desktop.domain.RemoteAccessSession s,
            String connectionHint,
            String deepLink) {
        var m = s.getMachine();
        return new SessionResponse(
                s.getId(),
                m.getId(),
                m.getRoomCode(),
                m.getHostname(),
                s.getOperatorUserId(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getStatus().name(),
                connectionHint,
                deepLink);
    }
}
