package ru.ruc.desktop.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record IssueConnectionTicketRequest(Long machineId, String remoteId, @Min(60) @Max(1800) Integer ttlSeconds) {}
