package ru.ruc.desktop.web.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.ruc.desktop.service.MediaStreamService;

@Component
public class MediaWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_TICKET = "ticket";
    private static final String ATTR_ROLE = "role";
    private static final String ATTR_ACTOR = "actor";
    private static final Set<String> ALLOWED_TYPES =
            Set.of("media-ready", "frame", "ping", "pong", "error");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_FRAME_CHARS = 2_000_000;

    private final MediaStreamService mediaStreamService;

    public MediaWebSocketHandler(MediaStreamService mediaStreamService) {
        this.mediaStreamService = mediaStreamService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> qp = splitQuery(session.getUri() == null ? null : session.getUri().getQuery());
        String ticket = qp.getOrDefault("ticket", "").trim();
        String role = qp.getOrDefault("role", "").trim().toLowerCase();
        String actor = qp.getOrDefault("actor", "unknown");

        if (ticket.isBlank() || (!"viewer".equals(role) && !"agent".equals(role))) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!mediaStreamService.validateTicketForRole(ticket, role)) {
            session.close(new CloseStatus(4401, "invalid ticket"));
            return;
        }

        session.getAttributes().put(ATTR_TICKET, ticket);
        session.getAttributes().put(ATTR_ROLE, role);
        session.getAttributes().put(ATTR_ACTOR, actor);
        mediaStreamService.register(ticket, role, actor, session);

        SignalEnvelope ack = new SignalEnvelope("ack", Instant.now().toEpochMilli(), "server", "backend", null);
        session.sendMessage(new TextMessage(MAPPER.writeValueAsString(ack)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String ticket = getAttr(session, ATTR_TICKET);
        String role = getAttr(session, ATTR_ROLE);
        if (ticket == null || role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "session not initialized");
        }
        if (message.getPayloadLength() > MAX_FRAME_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "frame too large");
        }

        SignalEnvelope incoming = parseAndValidate(message.getPayload());
        String actor = getAttr(session, ATTR_ACTOR);
        SignalEnvelope outgoing =
                new SignalEnvelope(
                        incoming.type(),
                        incoming.ts() != null ? incoming.ts() : Instant.now().toEpochMilli(),
                        role,
                        actor,
                        incoming.payload());
        mediaStreamService.relay(ticket, role, MAPPER.writeValueAsString(outgoing));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String ticket = getAttr(session, ATTR_TICKET);
        if (ticket != null) {
            mediaStreamService.unregister(ticket, session);
        }
    }

    private SignalEnvelope parseAndValidate(String payload) throws Exception {
        JsonNode root = MAPPER.readTree(payload);
        String type = root.path("type").asText("").trim();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported media message type: " + type);
        }
        if ("frame".equals(type)) {
            JsonNode p = root.path("payload");
            if (!p.hasNonNull("data") || p.path("data").asText("").isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frame without data");
            }
        }
        Long ts = root.hasNonNull("ts") ? root.path("ts").asLong() : null;
        String fromRole = root.path("fromRole").asText(null);
        String fromActor = root.path("fromActor").asText(null);
        JsonNode payloadNode = root.has("payload") ? root.path("payload") : null;
        return new SignalEnvelope(type, ts, fromRole, fromActor, payloadNode);
    }

    private static String getAttr(WebSocketSession session, String key) {
        Object v = session.getAttributes().get(key);
        return v == null ? null : v.toString();
    }

    private static Map<String, String> splitQuery(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                map.put(
                        java.net.URLDecoder.decode(part.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(part.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
