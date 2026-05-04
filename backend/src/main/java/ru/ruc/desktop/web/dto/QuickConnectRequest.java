package ru.ruc.desktop.web.dto;

import jakarta.validation.constraints.NotBlank;

public record QuickConnectRequest(@NotBlank String remoteId) {}
