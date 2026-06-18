package ru.ruc.desktop.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import ru.ruc.desktop.domain.ConnectionTicketStatus;
import ru.ruc.desktop.repository.ConnectionTicketRepository;

@Service
public class SignalingService {

    private final ConnectionTicketRepository ticketRepository;
    private final SessionAuditService auditService;

    // key = ticket token, value = pair sessions
    private final Map<String, PeerPair> rooms = new ConcurrentHashMap<>();

    public SignalingService(
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
                        return t.getStatus() == ConnectionTicketStatus.ISSUED;
                    }
                    return t.getStatus() == ConnectionTicketStatus.CONSUMED
                            || t.getStatus() == ConnectionTicketStatus.ISSUED;
                })
                .orElse(false);
    }

    public void register(String ticket, String role, String actor, WebSocketSession session) {
        rooms.compute(ticket, (k, prev) -> {
            PeerPair pair = prev == null ? new PeerPair() : prev;
            try {
                if ("viewer".equals(role)) {
                    pair.viewer = session;
                    flushPending(pair.pendingToViewer, session);
                } else {
                    pair.agent = session;
                    flushPending(pair.pendingToAgent, session);
                }
            } catch (IOException e) {
                throw new IllegalStateException("failed to flush pending signaling messages", e);
            }
            return pair;
        });
        auditService.log(actor, null, null, "SIGNALING_JOIN", "ticket=" + ticket + ", role=" + role);
    }

    public void markViewerConsumeTicket(String ticket, String actor) {
        ticketRepository
                .findByToken(ticket)
                .ifPresent(t -> {
                    if (t.getStatus() == ConnectionTicketStatus.ISSUED) {
                        t.setStatus(ConnectionTicketStatus.CONSUMED);
                        t.setConsumedAt(Instant.now());
                        auditService.log(actor, t.getMachineId(), t.getRemoteId(), "TICKET_CONSUMED_WS", ticket);
                    }
                });
    }

    public void relay(String ticket, String role, String payload) throws IOException {
        PeerPair pair = rooms.get(ticket);
        if (pair == null) {
            return;
        }
        WebSocketSession target = "viewer".equals(role) ? pair.agent : pair.viewer;
        Deque<String> pending = "viewer".equals(role) ? pair.pendingToAgent : pair.pendingToViewer;
        if (target != null && target.isOpen()) {
            target.sendMessage(new TextMessage(payload));
        } else {
            enqueue(pending, payload);
        }
    }

    private static void enqueue(Deque<String> pending, String payload) {
        if (pending.size() >= 64) {
            pending.pollFirst();
        }
        pending.addLast(payload);
    }

    private static void flushPending(Deque<String> pending, WebSocketSession session) throws IOException {
        while (!pending.isEmpty() && session.isOpen()) {
            session.sendMessage(new TextMessage(pending.pollFirst()));
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
        private final Deque<String> pendingToViewer = new ArrayDeque<>();
        private final Deque<String> pendingToAgent = new ArrayDeque<>();
    }
}
