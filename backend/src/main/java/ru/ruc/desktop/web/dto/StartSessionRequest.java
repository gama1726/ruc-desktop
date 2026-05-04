package ru.ruc.desktop.web.dto;

import jakarta.validation.constraints.NotNull;

public record StartSessionRequest(@NotNull Long machineId) {}
