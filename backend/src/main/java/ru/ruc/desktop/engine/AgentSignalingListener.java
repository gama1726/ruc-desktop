package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;

/** Handles viewer signaling messages on the agent side. */
final class AgentSignalingListener implements WebSocket.Listener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentConfig cfg;
    private final NativeHelperBridge helperBridge;
    private final StringBuilder textBuffer = new StringBuilder();

    AgentSignalingListener(AgentConfig cfg, NativeHelperBridge helperBridge) {
        this.cfg = cfg;
        this.helperBridge = helperBridge;
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
                case "viewer-ready" -> sendAgentReady(webSocket);
                case "offer" -> handleOffer(webSocket, node.path("payload"));
                case "answer" -> System.out.println("[agent] answer received: " + node.path("payload"));
                case "ice-candidate" -> handleRemoteCandidate(webSocket, node.path("payload"));
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

    private void sendAgentReady(WebSocket webSocket) {
        webSocket.sendText(
                "{\"type\":\"agent-ready\",\"ts\":"
                        + System.currentTimeMillis()
                        + ",\"payload\":{\"capabilities\":{\"webrtcBridge\":"
                        + (helperBridge != null)
                        + "},\"agentUid\":\""
                        + AgentUrls.escapeJson(cfg.agentUid())
                        + "\"}}",
                true);
    }

    private void handleOffer(WebSocket webSocket, JsonNode offerPayload) {
        if (helperBridge == null) {
            sendMockAnswerAndCandidate(webSocket, offerPayload);
            return;
        }
        helperBridge.sendToHelper("offer", offerPayload.toString());
        helperBridge.drainToSocket(webSocket);
    }

    private void sendMockAnswerAndCandidate(WebSocket webSocket, JsonNode offerPayload) {
        String offerSdp = offerPayload.path("sdp").asText("");
        webSocket.sendText(
                "{\"type\":\"answer\",\"ts\":"
                        + System.currentTimeMillis()
                        + ",\"payload\":{\"mode\":\"mock\",\"sdp\":\"\",\"sdpType\":\"answer\",\"note\":\"native webrtc bridge not configured\",\"offerSdpLength\":"
                        + offerSdp.length()
                        + "}}",
                true);
        webSocket.sendText(
                "{\"type\":\"ice-candidate\",\"ts\":"
                        + System.currentTimeMillis()
                        + ",\"payload\":{\"mode\":\"mock\",\"candidate\":\"demo-agent-candidate\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}}",
                true);
    }

    private void handleRemoteCandidate(WebSocket webSocket, JsonNode payload) {
        if (helperBridge == null) {
            System.out.println("[agent] candidate received: " + payload);
            return;
        }
        helperBridge.sendToHelper("ice-candidate", payload.toString());
        helperBridge.drainToSocket(webSocket);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (helperBridge != null) {
            helperBridge.shutdown();
        }
        System.out.println("[agent] ws close code=" + statusCode + ", reason=" + reason);
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (helperBridge != null) {
            helperBridge.shutdown();
        }
        System.out.println("[agent] ws error: " + error.getMessage());
        WebSocket.Listener.super.onError(webSocket, error);
    }
}
