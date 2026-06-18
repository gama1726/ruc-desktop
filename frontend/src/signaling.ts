import { signalingWsUrl, type ConnectionTicket } from "./api";

export type SignalType =
  | "ack"
  | "viewer-ready"
  | "agent-ready"
  | "offer"
  | "answer"
  | "ice-candidate"
  | "ping"
  | "pong"
  | "error";

export type SignalEnvelope = {
  type: SignalType;
  ts?: number;
  fromRole?: string;
  fromActor?: string;
  payload?: unknown;
};

type ViewerSignalingCallbacks = {
  onStatus: (status: "connecting" | "connected" | "closed") => void;
  onLog: (line: string) => void;
  onRemoteVideoStream?: (stream: MediaStream) => void;
};

export class ViewerSignalingClient {
  private socket: WebSocket | null = null;
  private peer: RTCPeerConnection | null = null;

  constructor(private readonly callbacks: ViewerSignalingCallbacks) {}

  connect(ticket: ConnectionTicket, actor: string) {
    this.disconnect();
    this.callbacks.onStatus("connecting");
    this.peer = this.createPeerConnection();
    const ws = new WebSocket(signalingWsUrl(ticket.token, "viewer", actor));

    ws.onopen = () => {
      this.callbacks.onStatus("connected");
      this.callbacks.onLog(`[open] ticket=${ticket.token}`);
      this.send({ type: "viewer-ready", ts: Date.now() });
      void this.createAndSendOffer();
    };

    ws.onmessage = (event) => {
      this.handleIncoming(String(event.data));
    };

    ws.onerror = () => {
      this.callbacks.onLog("[error] signaling socket error");
    };

    ws.onclose = (e) => {
      this.callbacks.onStatus("closed");
      this.callbacks.onLog(`[close] code=${e.code} reason=${e.reason || "-"}`);
      this.socket = null;
    };

    this.socket = ws;
  }

  disconnect() {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.close(1000, "manual-close");
    }
    this.socket = null;
    if (this.peer) {
      this.peer.close();
      this.peer = null;
    }
    this.callbacks.onStatus("closed");
  }

  private send(message: SignalEnvelope) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this.socket.send(JSON.stringify(message));
  }

  private handleIncoming(raw: string) {
    this.callbacks.onLog(`[msg] ${raw}`);
    let message: SignalEnvelope | null = null;
    try {
      message = JSON.parse(raw) as SignalEnvelope;
    } catch {
      this.callbacks.onLog("[warn] invalid signaling envelope");
      return;
    }

    switch (message.type) {
      case "offer":
        void this.handleOffer(message.payload);
        break;
      case "answer":
        void this.handleAnswer(message.payload);
        break;
      case "agent-ready":
        this.callbacks.onLog("[webrtc] agent ready");
        break;
      case "ping":
        this.send({ type: "pong", ts: Date.now() });
        break;
      case "ice-candidate":
        void this.handleRemoteCandidate(message.payload);
        break;
      default:
        break;
    }
  }

  private createPeerConnection(): RTCPeerConnection {
    const peer = new RTCPeerConnection({
      iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
    });
    peer.onicecandidate = (event) => {
      if (!event.candidate) {
        return;
      }
      this.send({
        type: "ice-candidate",
        ts: Date.now(),
        payload: {
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex,
        },
      });
    };
    peer.onconnectionstatechange = () => {
      this.callbacks.onLog(`[webrtc] state=${peer.connectionState}`);
    };
    peer.ontrack = (event) => {
      if (event.streams[0]) {
        this.callbacks.onLog("[webrtc] remote video track received");
        this.callbacks.onRemoteVideoStream?.(event.streams[0]);
      }
    };
    return peer;
  }

  private async createAndSendOffer() {
    if (!this.peer) {
      return;
    }
    try {
      const offer = await this.peer.createOffer({
        offerToReceiveAudio: true,
        offerToReceiveVideo: true,
      });
      await this.peer.setLocalDescription(offer);
      this.send({
        type: "offer",
        ts: Date.now(),
        payload: {
          sdp: offer.sdp,
          sdpType: offer.type,
        },
      });
      this.callbacks.onLog("[webrtc] local offer created");
    } catch (error) {
      this.callbacks.onLog(`[webrtc] offer error: ${String(error)}`);
    }
  }

  private async handleOffer(payload: unknown) {
    if (!this.peer) {
      return;
    }
    const sdp = (payload as { sdp?: string } | undefined)?.sdp;
    if (!sdp) {
      this.callbacks.onLog("[webrtc] offer without sdp");
      return;
    }
    try {
      await this.peer.setRemoteDescription({ type: "offer", sdp });
      const answer = await this.peer.createAnswer();
      await this.peer.setLocalDescription(answer);
      this.send({
        type: "answer",
        ts: Date.now(),
        payload: {
          sdp: answer.sdp,
          sdpType: answer.type,
        },
      });
      this.callbacks.onLog("[webrtc] answer sent");
    } catch (error) {
      this.callbacks.onLog(`[webrtc] handle offer error: ${String(error)}`);
    }
  }

  private async handleAnswer(payload: unknown) {
    if (!this.peer) {
      return;
    }
    const answerPayload = payload as { sdp?: string; mode?: string; note?: string } | undefined;
    if (answerPayload?.mode && answerPayload.mode !== "webrtc") {
      this.callbacks.onLog(
        `[webrtc] non-webrtc answer from agent (${answerPayload.mode}): ${answerPayload.note || "bridge not active"}`,
      );
      return;
    }
    const sdp = answerPayload?.sdp;
    if (!sdp) {
      this.callbacks.onLog("[webrtc] answer without sdp");
      return;
    }
    try {
      await this.peer.setRemoteDescription({ type: "answer", sdp });
      this.callbacks.onLog("[webrtc] remote answer applied");
    } catch (error) {
      this.callbacks.onLog(`[webrtc] handle answer error: ${String(error)}`);
    }
  }

  private async handleRemoteCandidate(payload: unknown) {
    if (!this.peer) {
      return;
    }
    const candidatePayload = payload as
      | { mode?: string; candidate?: string; sdpMid?: string | null; sdpMLineIndex?: number | null }
      | undefined;
    if (!candidatePayload?.candidate) {
      this.callbacks.onLog("[webrtc] empty remote candidate");
      return;
    }
    if (candidatePayload.mode && candidatePayload.mode !== "webrtc") {
      this.callbacks.onLog(`[webrtc] non-webrtc candidate from agent (${candidatePayload.mode})`);
      return;
    }
    try {
      await this.peer.addIceCandidate({
        candidate: candidatePayload.candidate,
        sdpMid: candidatePayload.sdpMid ?? null,
        sdpMLineIndex: candidatePayload.sdpMLineIndex ?? null,
      });
      this.callbacks.onLog("[webrtc] remote candidate applied");
    } catch (error) {
      this.callbacks.onLog(`[webrtc] add candidate error: ${String(error)}`);
    }
  }
}
