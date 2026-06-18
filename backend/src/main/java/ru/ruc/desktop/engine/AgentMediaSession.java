package ru.ruc.desktop.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Media WebSocket session and screen-capture publisher. */
final class AgentMediaSession {

    private final HttpClient http;
    private final AgentConfig cfg;
    private final AtomicBoolean captureRunning = new AtomicBoolean(false);
    private Thread captureThread;

    AgentMediaSession(HttpClient http, AgentConfig cfg) {
        this.http = http;
        this.cfg = cfg;
    }

    WebSocket connect(String ticket) {
        stopCapture();
        try {
            String wsUrl = AgentUrls.mediaWs(cfg, ticket);
            System.out.println("[agent] connect media ws: " + wsUrl);

            WebSocket ws =
                    http.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .buildAsync(
                                    URI.create(wsUrl),
                                    new WebSocket.Listener() {
                                        @Override
                                        public void onOpen(WebSocket webSocket) {
                                            System.out.println("[agent] media ws open");
                                            webSocket.sendText(
                                                    "{\"type\":\"media-ready\",\"ts\":"
                                                            + System.currentTimeMillis()
                                                            + ",\"payload\":{\"source\":\"java.awt.Robot\"}}",
                                                    true);
                                            WebSocket.Listener.super.onOpen(webSocket);
                                        }

                                        @Override
                                        public CompletionStage<?> onClose(
                                                WebSocket webSocket, int statusCode, String reason) {
                                            captureRunning.set(false);
                                            System.out.println(
                                                    "[agent] media ws close code="
                                                            + statusCode
                                                            + ", reason="
                                                            + reason);
                                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                                        }

                                        @Override
                                        public void onError(WebSocket webSocket, Throwable error) {
                                            captureRunning.set(false);
                                            System.out.println("[agent] media ws error: " + error.getMessage());
                                            WebSocket.Listener.super.onError(webSocket, error);
                                        }
                                    })
                            .join();

            if (cfg.screenCaptureEnabled()) {
                startCapture(ws);
            }
            return ws;
        } catch (Exception e) {
            System.out.println("[agent] media ws connect error: " + e.getMessage());
            return null;
        }
    }

    void stopCapture() {
        captureRunning.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
    }

    private void startCapture(WebSocket mediaSocket) {
        captureRunning.set(true);
        captureThread =
                new Thread(
                        new AgentScreenCapture(
                                mediaSocket,
                                captureRunning,
                                cfg.captureMaxWidth(),
                                cfg.captureIntervalMs(),
                                cfg.captureJpegQuality()),
                        "ruc-agent-screen-capture");
        captureThread.setDaemon(true);
        captureThread.start();
        System.out.println("[agent] screen capture started (maxWidth=" + cfg.captureMaxWidth() + ")");
    }

    static boolean isClosed(WebSocket socket) {
        return socket == null || socket.isOutputClosed() || socket.isInputClosed();
    }
}
