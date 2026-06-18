export type RemoteInputMessage =
  | { type: "mousemove"; x: number; y: number }
  | { type: "mousedown"; x: number; y: number; button: number }
  | { type: "mouseup"; x: number; y: number; button: number }
  | { type: "wheel"; x: number; y: number; deltaY: number }
  | { type: "keydown"; code: string; key: string }
  | { type: "keyup"; code: string; key: string }
  | { type: "select-screen"; id: number };

export type RemoteScreenInfo = {
  id: number;
  title: string;
  width: number;
  height: number;
};

export type RemoteScreensMessage = {
  type: "screens";
  screens: RemoteScreenInfo[];
  activeId: number;
};

type NormalizedPoint = { x: number; y: number };

type AttachRemoteInputOptions = {
  onScreens?: (message: RemoteScreensMessage) => void;
};

function mapClientToNormalized(
  video: HTMLVideoElement,
  clientX: number,
  clientY: number,
): NormalizedPoint | null {
  const rect = video.getBoundingClientRect();
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  if (!vw || !vh) {
    return null;
  }
  const scale = Math.min(rect.width / vw, rect.height / vh);
  const renderW = vw * scale;
  const renderH = vh * scale;
  const offsetX = rect.left + (rect.width - renderW) / 2;
  const offsetY = rect.top + (rect.height - renderH) / 2;
  const x = (clientX - offsetX) / renderW;
  const y = (clientY - offsetY) / renderH;
  if (x < 0 || x > 1 || y < 0 || y > 1) {
    return null;
  }
  return { x, y };
}

function send(channel: RTCDataChannel, message: RemoteInputMessage) {
  if (channel.readyState !== "open") {
    return;
  }
  channel.send(JSON.stringify(message));
}

export function selectRemoteScreen(channel: RTCDataChannel, id: number) {
  send(channel, { type: "select-screen", id });
}

/** Attach mouse/keyboard handlers on the remote video element. */
export function attachRemoteInput(
  video: HTMLVideoElement,
  channel: RTCDataChannel,
  options: AttachRemoteInputOptions = {},
): () => void {
  const container = video.parentElement;
  if (container) {
    container.tabIndex = 0;
    container.classList.add("remote-desktop-view--input");
  }

  let lastMoveAt = 0;

  const focusContainer = () => {
    container?.focus();
  };

  const onChannelMessage = (event: MessageEvent) => {
    try {
      const data = JSON.parse(String(event.data)) as { type?: string };
      if (data.type === "screens") {
        options.onScreens?.(data as RemoteScreensMessage);
      }
    } catch {
      // ignore non-json agent messages
    }
  };

  const onMouseMove = (event: MouseEvent) => {
    const now = Date.now();
    if (now - lastMoveAt < 33) {
      return;
    }
    lastMoveAt = now;
    const point = mapClientToNormalized(video, event.clientX, event.clientY);
    if (!point) {
      return;
    }
    send(channel, { type: "mousemove", x: point.x, y: point.y });
  };

  const onMouseDown = (event: MouseEvent) => {
    event.preventDefault();
    focusContainer();
    const point = mapClientToNormalized(video, event.clientX, event.clientY);
    if (!point) {
      return;
    }
    send(channel, { type: "mousedown", x: point.x, y: point.y, button: event.button });
  };

  const onMouseUp = (event: MouseEvent) => {
    event.preventDefault();
    const point = mapClientToNormalized(video, event.clientX, event.clientY);
    if (!point) {
      return;
    }
    send(channel, { type: "mouseup", x: point.x, y: point.y, button: event.button });
  };

  const onWheel = (event: WheelEvent) => {
    event.preventDefault();
    const point = mapClientToNormalized(video, event.clientX, event.clientY);
    if (!point) {
      return;
    }
    send(channel, { type: "wheel", x: point.x, y: point.y, deltaY: event.deltaY });
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.repeat) {
      return;
    }
    event.preventDefault();
    send(channel, { type: "keydown", code: event.code, key: event.key });
  };

  const onKeyUp = (event: KeyboardEvent) => {
    event.preventDefault();
    send(channel, { type: "keyup", code: event.code, key: event.key });
  };

  const onContextMenu = (event: Event) => {
    event.preventDefault();
  };

  channel.addEventListener("message", onChannelMessage);
  video.addEventListener("mousemove", onMouseMove);
  video.addEventListener("mousedown", onMouseDown);
  video.addEventListener("mouseup", onMouseUp);
  video.addEventListener("wheel", onWheel, { passive: false });
  video.addEventListener("contextmenu", onContextMenu);
  container?.addEventListener("keydown", onKeyDown);
  container?.addEventListener("keyup", onKeyUp);
  video.addEventListener("click", focusContainer);

  return () => {
    channel.removeEventListener("message", onChannelMessage);
    video.removeEventListener("mousemove", onMouseMove);
    video.removeEventListener("mousedown", onMouseDown);
    video.removeEventListener("mouseup", onMouseUp);
    video.removeEventListener("wheel", onWheel);
    video.removeEventListener("contextmenu", onContextMenu);
    container?.removeEventListener("keydown", onKeyDown);
    container?.removeEventListener("keyup", onKeyUp);
    video.removeEventListener("click", focusContainer);
    container?.classList.remove("remote-desktop-view--input");
  };
}
