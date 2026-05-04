package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Minimal agent-side client for Sprint 1.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Heartbeat to control-plane (/api/agents/heartbeat)</li>
 *   <li>Poll ticket by remoteId (/api/tickets/pull)</li>
 *   <li>Join signaling websocket as role=agent and relay basic messages</li>
 * </ul>
 */
public class AgentSignalingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        AgentConfig cfg = AgentConfig.fromEnv();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        System.out.println("[agent] starting with uid=" + cfg.agentUid + ", remoteId=" + cfg.remoteId);
        System.out.println("[agent] backend=" + cfg.backendBaseUrl);

        WebSocket socket = null;
        String activeTicket = null;
        Instant lastHeartbeat = Instant.EPOCH;

        while (true) {
            Instant now = Instant.now();
            if (Duration.between(lastHeartbeat, now).toSeconds() >= cfg.heartbeatIntervalSeconds) {
                sendHeartbeat(http, cfg);
                lastHeartbeat = now;
            }

            if (socket == null || socket.isOutputClosed() || socket.isInputClosed()) {
                Optional<String> maybeTicket = pullTicket(http, cfg);
                if (maybeTicket.isPresent() && !maybeTicket.get().equals(activeTicket)) {
                    activeTicket = maybeTicket.get();
                    socket = connectSignaling(cfg, activeTicket);
                }
            }

            Thread.sleep(2000L);
        }
    }

    private static void sendHeartbeat(HttpClient http, AgentConfig cfg) {
        try {
            String body =
                    """
                    {
                      "agentUid":"%s",
                      "displayName":"%s",
                      "machineId":%s,
                      "remoteId":"%s",
                      "ipAddress":"%s"
                    }
                    """
                            .formatted(
                                    escape(cfg.agentUid),
                                    escape(cfg.displayName),
                                    cfg.machineId == null ? "null" : cfg.machineId.toString(),
                                    escape(cfg.remoteId),
                                    escape(cfg.ipAddress));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.backendBaseUrl + "/api/agents/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                System.out.println("[agent] heartbeat failed: " + response.statusCode() + " body=" + response.body());
            } else {
                System.out.println("[agent] heartbeat ok");
            }
        } catch (Exception e) {
            System.out.println("[agent] heartbeat error: " + e.getMessage());
        }
    }

    private static Optional<String> pullTicket(HttpClient http, AgentConfig cfg) {
        try {
            String url = cfg.backendBaseUrl + "/api/tickets/pull?remoteId=" + urlEncode(cfg.remoteId);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                return Optional.empty();
            }
            if (response.statusCode() >= 300) {
                System.out.println("[agent] pull ticket failed: " + response.statusCode() + " body=" + response.body());
                return Optional.empty();
            }
            JsonNode node = MAPPER.readTree(response.body());
            String token = node.path("token").asText("");
            if (token.isBlank()) {
                return Optional.empty();
            }
            System.out.println("[agent] ticket received: " + token);
            return Optional.of(token);
        } catch (Exception e) {
            System.out.println("[agent] pull ticket error: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static WebSocket connectSignaling(AgentConfig cfg, String ticket) {
        try {
            String wsBase = cfg.backendBaseUrl.replaceFirst("^http", "ws");
            String wsUrl = wsBase + "/ws/signaling?ticket=" + urlEncode(ticket) + "&role=agent&actor=" + urlEncode(cfg.agentUid);
            System.out.println("[agent] connect ws: " + wsUrl);

            WebSocket ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(wsUrl), new AgentSocketListener(cfg))
                    .join();

            ws.sendText("{\"type\":\"agent-ready\",\"ts\":" + System.currentTimeMillis() + "}", true);
            return ws;
        } catch (Exception e) {
            System.out.println("[agent] ws connect error: " + e.getMessage());
            return null;
        }
    }

    private static String escape(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static final class AgentSocketListener implements WebSocket.Listener {
        private final AgentConfig cfg;
        private final StringBuilder textBuffer = new StringBuilder();

        private AgentSocketListener(AgentConfig cfg) {
            this.cfg = cfg;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("[agent] ws open");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                System.out.println("[agent] ws message: " + msg);
                handleSignal(webSocket, msg);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void handleSignal(WebSocket webSocket, String msg) {
            try {
                JsonNode node = MAPPER.readTree(msg);
                String type = node.path("type").asText("");
                switch (type) {
                    case "viewer-ready" -> sendOffer(webSocket);
                    case "offer" -> {
                        sendAnswer(webSocket, node.path("payload"));
                        sendIceCandidate(webSocket);
                    }
                    case "ping" -> webSocket.sendText(
                            "{\"type\":\"pong\",\"ts\":" + System.currentTimeMillis() + "}", true);
                    default -> {
                        // ignore unknown signal types in demo agent
                    }
                }
            } catch (Exception e) {
                System.out.println("[agent] ws parse error: " + e.getMessage());
            }
        }

        private void sendOffer(WebSocket webSocket) {
            webSocket.sendText(
                    "{\"type\":\"offer\",\"ts\":"
                            + System.currentTimeMillis()
                            + ",\"payload\":{\"sdp\":\"demo-offer-from-agent\",\"agentUid\":\""
                            + escape(cfg.agentUid)
                            + "\"}}",
                    true);
        }

        private void sendAnswer(WebSocket webSocket, JsonNode offerPayload) {
            String remoteSdp = offerPayload.path("sdp").asText("unknown-offer");
            webSocket.sendText(
                    "{\"type\":\"answer\",\"ts\":"
                            + System.currentTimeMillis()
                            + ",\"payload\":{\"sdp\":\"demo-answer-for-"
                            + escape(remoteSdp)
                            + "\"}}",
                    true);
        }

        private void sendIceCandidate(WebSocket webSocket) {
            webSocket.sendText(
                    "{\"type\":\"ice-candidate\",\"ts\":"
                            + System.currentTimeMillis()
                            + ",\"payload\":{\"candidate\":\"demo-agent-candidate\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}}",
                    true);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[agent] ws close code=" + statusCode + ", reason=" + reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("[agent] ws error: " + error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }

    private record AgentConfig(
            String backendBaseUrl,
            String agentUid,
            String displayName,
            Long machineId,
            String remoteId,
            String ipAddress,
            long heartbeatIntervalSeconds) {
        static AgentConfig fromEnv() {
            String base = getenv("RUC_BACKEND_BASE_URL", "http://localhost:8080");
            String uid = getenv("RUC_AGENT_UID", "agent-local-1");
            String displayName = getenv("RUC_AGENT_DISPLAY_NAME", "RUC Agent Local");
            String remoteId = getenv("RUC_AGENT_REMOTE_ID", "260227322");
            String ip = getenv("RUC_AGENT_IP", "127.0.0.1");
            Long machineId = parseLongOrNull(System.getenv("RUC_AGENT_MACHINE_ID"));
            long heartbeat = parseLongOrDefault(System.getenv("RUC_AGENT_HEARTBEAT_SECONDS"), 15L);
            return new AgentConfig(base, uid, displayName, machineId, remoteId, ip, heartbeat);
        }

        private static String getenv(String key, String def) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? def : value;
        }

        private static Long parseLongOrNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static long parseLongOrDefault(String value, long def) {
            if (value == null || value.isBlank()) {
                return def;
            }
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }
}
