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
    private AgentWebRtcBridge webRtcBridge;

    AgentSignalingSession(HttpClient http, AgentConfig cfg) {
        this.http = http;
        this.cfg = cfg;
    }

    WebSocket connect(String ticket) {
        shutdownBridges();
        try {
            String wsUrl = AgentUrls.signalingWs(cfg, ticket);
            System.out.println("[agent] connect ws: " + wsUrl);

            helperBridge = NativeHelperBridge.tryStart(cfg);
            if (helperBridge == null && cfg.webrtcJavaEnabled()) {
                webRtcBridge = AgentWebRtcBridge.tryCreate(cfg);
            }

            WebSocket ws =
                    http.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .buildAsync(
                                    URI.create(wsUrl),
                                    new AgentSignalingListener(cfg, helperBridge, webRtcBridge))
                            .join();

            ws.sendText("{\"type\":\"agent-ready\",\"ts\":" + System.currentTimeMillis() + "}", true);
            return ws;
        } catch (Exception e) {
            System.out.println("[agent] ws connect error: " + e.getMessage());
            shutdownBridges();
            return null;
        }
    }

    private void shutdownBridges() {
        if (helperBridge != null) {
            helperBridge.shutdown();
            helperBridge = null;
        }
        if (webRtcBridge != null) {
            webRtcBridge.shutdown();
            webRtcBridge = null;
        }
    }

    static boolean isClosed(WebSocket socket) {
        return socket == null || socket.isOutputClosed() || socket.isInputClosed();
    }
}
