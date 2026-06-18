package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCDataChannel;
import dev.onvoid.webrtc.RTCDataChannelBuffer;
import dev.onvoid.webrtc.RTCDataChannelObserver;
import dev.onvoid.webrtc.RTCDataChannelState;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpReceiver;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSignalingState;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaStream;
import dev.onvoid.webrtc.media.video.VideoDesktopSource;
import dev.onvoid.webrtc.media.video.VideoTrack;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** In-process WebRTC bridge: desktop capture and SDP/ICE for the browser viewer. */
final class AgentWebRtcBridge implements PeerConnectionObserver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PeerConnectionFactory FACTORY = new PeerConnectionFactory();

    private final AgentConfig cfg;
    private final AgentCaptureDisplay captureDisplay;
    private final AgentRemoteInput remoteInput;
    private final Object lock = new Object();

    private RTCPeerConnection peerConnection;
    private VideoDesktopSource videoSource;
    private VideoTrack videoTrack;
    private RTCDataChannel inputChannel;
    private WebSocket activeSocket;
    private final Deque<JsonNode> pendingRemoteCandidates = new ArrayDeque<>();

    private AgentWebRtcBridge(AgentConfig cfg, AgentCaptureDisplay captureDisplay, AgentRemoteInput remoteInput) {
        this.cfg = cfg;
        this.captureDisplay = captureDisplay;
        this.remoteInput = remoteInput;
    }

    static AgentWebRtcBridge tryCreate(AgentConfig cfg) {
        try {
            AgentCaptureDisplay captureDisplay = new AgentCaptureDisplay();
            AgentRemoteInput remoteInput = AgentRemoteInput.tryCreate(captureDisplay);
            if (remoteInput == null) {
                return null;
            }
            AgentWebRtcBridge bridge = new AgentWebRtcBridge(cfg, captureDisplay, remoteInput);
            System.out.println("[agent] java webrtc bridge ready (webrtc-java)");
            return bridge;
        } catch (Throwable t) {
            System.out.println("[agent] java webrtc bridge unavailable: " + t.getMessage());
            return null;
        }
    }

    boolean isActive() {
        return true;
    }

    void handleOffer(WebSocket webSocket, JsonNode offerPayload) {
        String remoteSdp = offerPayload.path("sdp").asText("");
        String remoteType = offerPayload.path("sdpType").asText("offer");
        if (remoteSdp.isBlank()) {
            System.out.println("[agent] webrtc offer without sdp");
            return;
        }

        synchronized (lock) {
            activeSocket = webSocket;
            if (peerConnection != null) {
                RTCPeerConnectionState state = peerConnection.getConnectionState();
                if (state == RTCPeerConnectionState.CONNECTED || state == RTCPeerConnectionState.CONNECTING) {
                    System.out.println("[agent] offer ignored: connection already " + state);
                    return;
                }
            }
            closePeerConnectionLocked();
            pendingRemoteCandidates.clear();
            try {
                RTCConfiguration config = new RTCConfiguration();
                RTCIceServer iceServer = new RTCIceServer();
                iceServer.urls.add("stun:stun.l.google.com:19302");
                config.iceServers.add(iceServer);

                peerConnection = FACTORY.createPeerConnection(config, this);
                attachDesktopVideoTrack();

                RTCSdpType sdpType = "answer".equalsIgnoreCase(remoteType) ? RTCSdpType.ANSWER : RTCSdpType.OFFER;
                RTCSessionDescription remoteDescription = new RTCSessionDescription(sdpType, remoteSdp);
                peerConnection.setRemoteDescription(
                        remoteDescription,
                        new SetSessionDescriptionObserver() {
                            @Override
                            public void onSuccess() {
                                flushPendingCandidates();
                                createAndSendAnswer(webSocket);
                            }

                            @Override
                            public void onFailure(String error) {
                                System.out.println("[agent] webrtc setRemoteDescription failed: " + error);
                            }
                        });
            } catch (Exception e) {
                System.out.println("[agent] webrtc offer handling failed: " + e.getMessage());
                closePeerConnectionLocked();
            }
        }
    }

    void handleRemoteCandidate(JsonNode payload) {
        String candidate = payload.path("candidate").asText("");
        if (candidate.isBlank()) {
            return;
        }

        synchronized (lock) {
            if (peerConnection == null) {
                enqueueCandidate(payload);
                return;
            }
            applyRemoteCandidate(payload);
        }
    }

    private void enqueueCandidate(JsonNode payload) {
        if (pendingRemoteCandidates.size() >= 64) {
            pendingRemoteCandidates.pollFirst();
        }
        pendingRemoteCandidates.addLast(payload);
    }

    private void flushPendingCandidates() {
        while (!pendingRemoteCandidates.isEmpty()) {
            applyRemoteCandidate(pendingRemoteCandidates.pollFirst());
        }
    }

    private void applyRemoteCandidate(JsonNode payload) {
        if (peerConnection == null) {
            return;
        }
        String candidate = payload.path("candidate").asText("");
        String sdpMid = payload.path("sdpMid").asText("0");
        int sdpMLineIndex = payload.path("sdpMLineIndex").asInt(0);
        try {
            peerConnection.addIceCandidate(new RTCIceCandidate(sdpMid, sdpMLineIndex, candidate));
        } catch (Exception e) {
            System.out.println("[agent] webrtc addIceCandidate failed: " + e.getMessage());
        }
    }

    void shutdown() {
        synchronized (lock) {
            activeSocket = null;
            closePeerConnectionLocked();
        }
    }

    private void createAndSendAnswer(WebSocket webSocket) {
        if (peerConnection == null) {
            return;
        }
        peerConnection.createAnswer(
                new RTCAnswerOptions(),
                new CreateSessionDescriptionObserver() {
                    @Override
                    public void onSuccess(RTCSessionDescription description) {
                        if (peerConnection == null) {
                            return;
                        }
                        peerConnection.setLocalDescription(
                                description,
                                new SetSessionDescriptionObserver() {
                                    @Override
                                    public void onSuccess() {
                                        sendAnswer(webSocket, description);
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        System.out.println("[agent] webrtc setLocalDescription failed: " + error);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String error) {
                        System.out.println("[agent] webrtc createAnswer failed: " + error);
                    }
                });
    }

    private void attachDesktopVideoTrack() {
        videoSource = new VideoDesktopSource();
        int maxWidth = Math.max(640, cfg.captureMaxWidth());
        int maxHeight = (int) (maxWidth * 9L / 16L);
        videoSource.setFrameRate(cfg.webrtcMaxFps());
        videoSource.setMaxFrameSize(maxWidth, maxHeight);
        captureDisplay.reloadScreens();
        captureDisplay.applyToCapture(videoSource);
        videoSource.start();

        videoTrack = FACTORY.createVideoTrack("screen", videoSource);
        List<String> streamIds = new ArrayList<>();
        streamIds.add("ruc-screen");
        peerConnection.addTrack(videoTrack, streamIds);
        System.out.println("[agent] webrtc desktop capture started (" + maxWidth + "x" + maxHeight + ")");
    }

    private boolean switchCaptureScreen(int desktopId) {
        if (!captureDisplay.selectByDesktopId(desktopId)) {
            return false;
        }
        if (videoSource != null) {
            captureDisplay.applyToCapture(videoSource);
            System.out.println("[agent] switched capture to screen id=" + desktopId);
            return true;
        }
        return false;
    }

    private void sendDataChannelText(RTCDataChannel channel, String text) {
        if (channel == null || channel.getState() != RTCDataChannelState.OPEN) {
            return;
        }
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            channel.send(new RTCDataChannelBuffer(ByteBuffer.wrap(bytes), false));
        } catch (Exception e) {
            System.out.println("[agent] data channel send failed: " + e.getMessage());
        }
    }

    private void handleDataChannelMessage(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String type = node.path("type").asText("");
            if ("select-screen".equals(type)) {
                int screenId = node.path("id").asInt(-1);
                if (switchCaptureScreen(screenId) && inputChannel != null) {
                    sendDataChannelText(inputChannel, captureDisplay.screensJson());
                }
                return;
            }
            if (remoteInput != null) {
                remoteInput.handleMessage(json);
            }
        } catch (Exception e) {
            System.out.println("[agent] data channel message error: " + e.getMessage());
        }
    }

    private void sendAnswer(WebSocket webSocket, RTCSessionDescription description) {
        if (webSocket == null) {
            return;
        }
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "answer");
            root.put("ts", System.currentTimeMillis());
            ObjectNode payload = root.putObject("payload");
            payload.put("mode", "webrtc");
            payload.put("sdp", description.sdp);
            payload.put("sdpType", "answer");
            webSocket.sendText(MAPPER.writeValueAsString(root), true);
            System.out.println("[agent] webrtc answer sent");
        } catch (Exception e) {
            System.out.println("[agent] webrtc answer send failed: " + e.getMessage());
        }
    }

    private void sendIceCandidate(WebSocket webSocket, RTCIceCandidate candidate) {
        if (webSocket == null || candidate == null || candidate.sdp == null) {
            return;
        }
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "ice-candidate");
            root.put("ts", System.currentTimeMillis());
            ObjectNode payload = root.putObject("payload");
            payload.put("mode", "webrtc");
            payload.put("candidate", candidate.sdp);
            payload.put("sdpMid", candidate.sdpMid == null ? "0" : candidate.sdpMid);
            payload.put("sdpMLineIndex", candidate.sdpMLineIndex);
            webSocket.sendText(MAPPER.writeValueAsString(root), true);
        } catch (Exception e) {
            System.out.println("[agent] webrtc ice send failed: " + e.getMessage());
        }
    }

    private void closePeerConnectionLocked() {
        videoTrack = null;
        if (inputChannel != null) {
            try {
                inputChannel.unregisterObserver();
                inputChannel.close();
            } catch (Exception ignored) {
                // ignore
            }
            inputChannel = null;
        }
        if (videoSource != null) {
            try {
                videoSource.stop();
                videoSource.dispose();
            } catch (Exception ignored) {
                // ignore
            }
            videoSource = null;
        }
        if (peerConnection != null) {
            try {
                peerConnection.close();
            } catch (Exception ignored) {
                // ignore
            }
            peerConnection = null;
        }
        pendingRemoteCandidates.clear();
    }

    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
        sendIceCandidate(activeSocket, candidate);
    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {
        System.out.println("[agent] webrtc connection state: " + state);
    }

    @Override
    public void onIceConnectionChange(RTCIceConnectionState state) {
        System.out.println("[agent] webrtc ice connection: " + state);
    }

    @Override
    public void onIceGatheringChange(RTCIceGatheringState state) {
        System.out.println("[agent] webrtc ice gathering: " + state);
    }

    @Override
    public void onSignalingChange(RTCSignalingState state) {
        System.out.println("[agent] webrtc signaling: " + state);
    }

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {
        if (!"ruc-input".equals(dataChannel.getLabel())) {
            System.out.println("[agent] webrtc data channel ignored: " + dataChannel.getLabel());
            return;
        }
        inputChannel = dataChannel;
        dataChannel.registerObserver(
                new RTCDataChannelObserver() {
                    @Override
                    public void onBufferedAmountChange(long previousAmount) {
                        // no-op
                    }

                    @Override
                    public void onStateChange() {
                        System.out.println("[agent] input channel state: " + dataChannel.getState());
                        if (dataChannel.getState() == RTCDataChannelState.OPEN) {
                            sendDataChannelText(dataChannel, captureDisplay.screensJson());
                        }
                    }

                    @Override
                    public void onMessage(RTCDataChannelBuffer buffer) {
                        if (buffer.binary) {
                            return;
                        }
                        byte[] bytes = new byte[buffer.data.remaining()];
                        buffer.data.get(bytes);
                        handleDataChannelMessage(new String(bytes, StandardCharsets.UTF_8));
                    }
                });
        System.out.println("[agent] input channel attached");
    }

    @Override
    public void onRenegotiationNeeded() {
        // viewer drives negotiation
    }

    @Override
    public void onAddTrack(RTCRtpReceiver receiver, MediaStream[] mediaStreams) {
        System.out.println("[agent] webrtc remote track added");
    }

    @Override
    public void onRemoveTrack(RTCRtpReceiver receiver) {
        System.out.println("[agent] webrtc remote track removed");
    }

    @Override
    public void onTrack(RTCRtpTransceiver transceiver) {
        System.out.println("[agent] webrtc transceiver track");
    }
}
