package ru.ruc.desktop.service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import ru.ruc.desktop.domain.ConnectionTicketStatus;
import ru.ruc.desktop.repository.ConnectionTicketRepository;

@Service
public class MediaStreamService {

    private final ConnectionTicketRepository ticketRepository;
    private final SessionAuditService auditService;
    private final Map<String, PeerPair> rooms = new ConcurrentHashMap<>();

    public MediaStreamService(
            ConnectionTicketRepository ticketRepository, SessionAuditService auditService) {
        this.ticketRepository = ticketRepository;
        this.auditService = auditService;
    }

    public boolean validateTicketForRole(String ticket, String role) {
        return ticketRepository
                .findByToken(ticket)
                .map(t -> {
                    if (t.getExpiresAt().isBefore(Instant.now())) {
                        return false;
                    }
                    if ("viewer".equals(role)) {
                        return t.getStatus() == ConnectionTicketStatus.ISSUED
                                || t.getStatus() == ConnectionTicketStatus.CONSUMED;
                    }
                    return t.getStatus() == ConnectionTicketStatus.CONSUMED
                            || t.getStatus() == ConnectionTicketStatus.ISSUED;
                })
                .orElse(false);
    }

    public void register(String ticket, String role, String actor, WebSocketSession session) {
        rooms.compute(ticket, (k, prev) -> {
            PeerPair pair = prev == null ? new PeerPair() : prev;
            if ("viewer".equals(role)) {
                pair.viewer = session;
            } else {
                pair.agent = session;
            }
            return pair;
        });
        auditService.log(actor, null, null, "MEDIA_JOIN", "ticket=" + ticket + ", role=" + role);
    }

    public void relay(String ticket, String role, String payload) throws IOException {
        PeerPair pair = rooms.get(ticket);
        if (pair == null) {
            return;
        }
        WebSocketSession target = "viewer".equals(role) ? pair.agent : pair.viewer;
        if (target != null && target.isOpen()) {
            target.sendMessage(new TextMessage(payload));
        }
    }

    public void unregister(String ticket, WebSocketSession session) {
        rooms.computeIfPresent(ticket, (k, pair) -> {
            if (pair.viewer == session) {
                pair.viewer = null;
            }
            if (pair.agent == session) {
                pair.agent = null;
            }
            if (pair.viewer == null && pair.agent == null) {
                return null;
            }
            return pair;
        });
    }

    private static class PeerPair {
        private WebSocketSession viewer;
        private WebSocketSession agent;
    }
}
