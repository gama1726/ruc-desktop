package ru.ruc.desktop.web.dto;

import java.util.List;

public record EngineHintResponse(
        String idServer,
        String relayAddress,
        String publicKeyFileHint,
        String deepLinkTemplate,
        List<String> peerIds,
        List<String> steps) {}
