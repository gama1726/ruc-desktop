package ru.ruc.desktop.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;

/** Signaling WebSocket session for role=agent. */
final class AgentSignalingSession {

    private final HttpClient http;
    private final AgentConfig cfg;
    private NativeHelperBridge helperBridge;

    AgentSignalingSession(HttpClient http, AgentConfig cfg) {
        this.http = http;
        this.cfg = cfg;
    }

    WebSocket connect(String ticket) {
        try {
            String wsUrl = AgentUrls.signalingWs(cfg, ticket);
            System.out.println("[agent] connect ws: " + wsUrl);

            helperBridge = NativeHelperBridge.tryStart(cfg);
            WebSocket ws =
                    http.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .buildAsync(URI.create(wsUrl), new AgentSignalingListener(cfg, helperBridge))
                            .join();

            ws.sendText("{\"type\":\"agent-ready\",\"ts\":" + System.currentTimeMillis() + "}", true);
            return ws;
        } catch (Exception e) {
            System.out.println("[agent] ws connect error: " + e.getMessage());
            if (helperBridge != null) {
                helperBridge.shutdown();
                helperBridge = null;
            }
            return null;
        }
    }

    static boolean isClosed(WebSocket socket) {
        return socket == null || socket.isOutputClosed() || socket.isInputClosed();
    }
}
