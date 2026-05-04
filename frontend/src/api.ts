const USER_STORAGE_KEY = "ruc-desktop-operator-id";

export function getOperatorId(): string {
  return localStorage.getItem(USER_STORAGE_KEY) || "demo";
}

export function setOperatorId(id: string): void {
  localStorage.setItem(USER_STORAGE_KEY, id);
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-Ruc-User": getOperatorId(),
      ...init?.headers,
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export type Machine = {
  id: number;
  roomCode: string;
  hostname: string;
  enginePeerId: string | null;
  active: boolean;
};

export type Session = {
  id: number;
  machineId: number;
  roomCode: string;
  hostname: string;
  operatorUserId: string;
  startedAt: string;
  endedAt: string | null;
  status: string;
  connectionHint: string;
  deepLink: string | null;
};

export function fetchMachines(): Promise<Machine[]> {
  return api<Machine[]>("/api/machines");
}

export function fetchActiveSessions(): Promise<Session[]> {
  return api<Session[]>("/api/sessions/active");
}

export function startSession(machineId: number): Promise<Session> {
  return api<Session>("/api/sessions", {
    method: "POST",
    body: JSON.stringify({ machineId }),
  });
}

export function closeSession(sessionId: number): Promise<Session> {
  return api<Session>(`/api/sessions/${sessionId}/close`, {
    method: "PATCH",
  });
}
