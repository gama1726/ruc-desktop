package ru.ruc.desktop.web.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignalEnvelope(
        String type,
        Long ts,
        String fromRole,
        String fromActor,
        JsonNode payload) {}
