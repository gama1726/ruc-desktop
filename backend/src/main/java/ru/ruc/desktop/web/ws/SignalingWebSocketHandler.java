package ru.ruc.desktop.web.ws;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import ru.ruc.desktop.service.SignalingService;

@Component
public class SignalingWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_TICKET = "ticket";
    private static final String ATTR_ROLE = "role";
    private static final String ATTR_ACTOR = "actor";

    private final SignalingService signalingService;

    public SignalingWebSocketHandler(SignalingService signalingService) {
        this.signalingService = signalingService;
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
        if (!signalingService.validateTicketForRole(ticket, role)) {
            session.close(new CloseStatus(4401, "invalid ticket"));
            return;
        }

        session.getAttributes().put(ATTR_TICKET, ticket);
        session.getAttributes().put(ATTR_ROLE, role);
        session.getAttributes().put(ATTR_ACTOR, actor);
        signalingService.register(ticket, role, actor, session);

        if ("viewer".equals(role)) {
            signalingService.markViewerConsumeTicket(ticket, actor);
        }
        session.sendMessage(new TextMessage("{\"type\":\"ack\",\"role\":\"" + role + "\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String ticket = getAttr(session, ATTR_TICKET);
        String role = getAttr(session, ATTR_ROLE);
        if (ticket == null || role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "session not initialized");
        }
        signalingService.relay(ticket, role, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String ticket = getAttr(session, ATTR_TICKET);
        if (ticket != null) {
            signalingService.unregister(ticket, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String ticket = getAttr(session, ATTR_TICKET);
        if (ticket != null) {
            signalingService.unregister(ticket, session);
        }
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String getAttr(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value == null ? null : value.toString();
    }

    private Map<String, String> splitQuery(String query) throws IOException {
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            String[] chunks = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(chunks[0], java.nio.charset.StandardCharsets.UTF_8);
            String val =
                    chunks.length > 1
                            ? java.net.URLDecoder.decode(chunks[1], java.nio.charset.StandardCharsets.UTF_8)
                            : "";
            result.put(key, val);
        }
        return result;
    }
}
