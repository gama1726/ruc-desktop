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
};

export class ViewerSignalingClient {
  private socket: WebSocket | null = null;

  constructor(private readonly callbacks: ViewerSignalingCallbacks) {}

  connect(ticket: ConnectionTicket, actor: string) {
    this.disconnect();
    this.callbacks.onStatus("connecting");
    const ws = new WebSocket(signalingWsUrl(ticket.token, "viewer", actor));

    ws.onopen = () => {
      this.callbacks.onStatus("connected");
      this.callbacks.onLog(`[open] ticket=${ticket.token}`);
      this.send({ type: "viewer-ready", ts: Date.now() });
      this.send({
        type: "offer",
        ts: Date.now(),
        payload: { sdp: "demo-offer-from-viewer" },
      });
    };

    ws.onmessage = (event) => {
      this.callbacks.onLog(`[msg] ${String(event.data)}`);
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
    this.callbacks.onStatus("closed");
  }

  private send(message: SignalEnvelope) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this.socket.send(JSON.stringify(message));
  }
}
