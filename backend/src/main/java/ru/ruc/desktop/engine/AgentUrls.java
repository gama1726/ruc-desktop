package ru.ruc.desktop.engine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class AgentUrls {

    private AgentUrls() {}

    static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String wsBase(String backendBaseUrl) {
        return backendBaseUrl.replaceFirst("^http", "ws");
    }

    static String signalingWs(AgentConfig cfg, String ticket) {
        return wsBase(cfg.backendBaseUrl())
                + "/ws/signaling?ticket="
                + urlEncode(ticket)
                + "&role=agent&actor="
                + urlEncode(cfg.agentUid());
    }

    static String mediaWs(AgentConfig cfg, String ticket) {
        return wsBase(cfg.backendBaseUrl())
                + "/ws/media?ticket="
                + urlEncode(ticket)
                + "&role=agent&actor="
                + urlEncode(cfg.agentUid());
    }
}
