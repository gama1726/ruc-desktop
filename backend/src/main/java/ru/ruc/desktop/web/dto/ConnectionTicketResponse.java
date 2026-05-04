package ru.ruc.desktop.web.dto;

import java.time.Instant;

public record ConnectionTicketResponse(
        String token,
        Long machineId,
        String remoteId,
        Instant expiresAt,
        String status,
        String deepLink,
        String connectionHint) {}
