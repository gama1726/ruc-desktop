package ru.ruc.desktop.engine;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Host agent runtime: control-plane heartbeat, ticket pull, signaling and media sessions.
 */
final class AgentRuntime {

    private static final long LOOP_SLEEP_MS = 2000L;

    private final AgentConfig cfg;
    private final AgentControlPlaneClient controlPlane;
    private final AgentSignalingSession signalingSession;
    private final AgentMediaSession mediaSession;

    private WebSocket signalingSocket;
    private WebSocket mediaSocket;
    private String activeTicket;
    private Instant lastHeartbeat = Instant.EPOCH;

    AgentRuntime(AgentConfig cfg) {
        this.cfg = cfg;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.controlPlane = new AgentControlPlaneClient(http, cfg);
        this.signalingSession = new AgentSignalingSession(http, cfg);
        this.mediaSession = new AgentMediaSession(http, cfg);
    }

    void runForever() throws InterruptedException {
        System.out.println("[agent] starting with uid=" + cfg.agentUid() + ", remoteId=" + cfg.remoteId());
        System.out.println("[agent] backend=" + cfg.backendBaseUrl());
        System.out.println("[agent] native helper=" + (cfg.helperPath() == null ? "disabled" : cfg.helperPath()));
        System.out.println("[agent] java webrtc=" + cfg.webrtcJavaEnabled());
        System.out.println("[agent] jpeg media=" + cfg.screenCaptureEnabled());

        while (true) {
            tickHeartbeat();
            ensureSessions();
            Thread.sleep(LOOP_SLEEP_MS);
        }
    }

    private void tickHeartbeat() {
        Instant now = Instant.now();
        if (Duration.between(lastHeartbeat, now).toSeconds() >= cfg.heartbeatIntervalSeconds()) {
            controlPlane.sendHeartbeat();
            lastHeartbeat = now;
        }
    }

    private void ensureSessions() {
        boolean signalingDown = AgentSignalingSession.isClosed(signalingSocket);
        boolean mediaDown = AgentMediaSession.isClosed(mediaSocket);
        if (!signalingDown && !mediaDown) {
            return;
        }

        Optional<String> maybeTicket = controlPlane.pullTicket();
        if (maybeTicket.isEmpty()) {
            return;
        }

        String ticket = maybeTicket.get();
        if (!ticket.equals(activeTicket)) {
            mediaSession.stopCapture();
            activeTicket = ticket;
        }

        if (signalingDown) {
            signalingSocket = signalingSession.connect(activeTicket);
        }
        if (mediaDown) {
            mediaSocket = mediaSession.connect(activeTicket);
        }
    }
}
