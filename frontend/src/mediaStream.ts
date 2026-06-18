import { getOperatorId } from "./api";

export type MediaFramePayload = {
  mime?: string;
  width?: number;
  height?: number;
  data?: string;
};

type MediaCallbacks = {
  onStatus: (status: "connecting" | "connected" | "closed") => void;
  onLog: (line: string) => void;
  onFrame: (payload: MediaFramePayload) => void;
};

function mediaWsUrl(ticket: string, role: "viewer" | "agent", actor: string): string {
  const proto = window.location.protocol === "https:" ? "wss" : "ws";
  const host = window.location.host;
  const p = new URLSearchParams({ ticket, role, actor });
  return `${proto}://${host}/ws/media?${p.toString()}`;
}

export class ViewerMediaClient {
  private socket: WebSocket | null = null;

  constructor(private readonly callbacks: MediaCallbacks) {}

  connect(ticket: string) {
    this.disconnect();
    this.callbacks.onStatus("connecting");
    const actor = getOperatorId() || "demo";
    const ws = new WebSocket(mediaWsUrl(ticket, "viewer", actor));

    ws.onopen = () => {
      this.callbacks.onStatus("connected");
      this.callbacks.onLog(`[media open] ticket=${ticket}`);
      ws.send(JSON.stringify({ type: "media-ready", ts: Date.now() }));
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(String(event.data)) as { type?: string; payload?: MediaFramePayload };
        if (msg.type === "frame" && msg.payload?.data) {
          this.callbacks.onFrame(msg.payload);
        } else if (msg.type === "ack") {
          this.callbacks.onLog("[media] ack from server");
        }
      } catch {
        this.callbacks.onLog("[media] invalid frame message");
      }
    };

    ws.onerror = () => this.callbacks.onLog("[media] socket error");
    ws.onclose = (e) => {
      this.callbacks.onStatus("closed");
      this.callbacks.onLog(`[media close] code=${e.code}`);
      this.socket = null;
    };

    this.socket = ws;
  }

  disconnect() {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.close(1000, "manual-close");
    }
    this.socket = null;
    this.callbacks.onStatus("closed");
  }
}
