package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** HTTP integration with the control-plane: heartbeat and ticket pull. */
final class AgentControlPlaneClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final AgentConfig cfg;

    AgentControlPlaneClient(HttpClient http, AgentConfig cfg) {
        this.http = http;
        this.cfg = cfg;
    }

    void sendHeartbeat() {
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
                                    AgentUrls.escapeJson(cfg.agentUid()),
                                    AgentUrls.escapeJson(cfg.displayName()),
                                    cfg.machineId() == null ? "null" : cfg.machineId().toString(),
                                    AgentUrls.escapeJson(cfg.remoteId()),
                                    AgentUrls.escapeJson(cfg.ipAddress()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.backendBaseUrl() + "/api/agents/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                System.out.println(
                        "[agent] heartbeat failed: " + response.statusCode() + " body=" + response.body());
            } else {
                System.out.println("[agent] heartbeat ok");
            }
        } catch (Exception e) {
            System.out.println("[agent] heartbeat error: " + e.getMessage());
        }
    }

    Optional<String> pullTicket() {
        try {
            String url = cfg.backendBaseUrl() + "/api/tickets/pull?remoteId=" + AgentUrls.urlEncode(cfg.remoteId());
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                return Optional.empty();
            }
            if (response.statusCode() >= 300) {
                System.out.println(
                        "[agent] pull ticket failed: " + response.statusCode() + " body=" + response.body());
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
}
