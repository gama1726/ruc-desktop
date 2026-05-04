import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
import {
  closeSession,
  consumeConnectionTicket,
  fetchActiveSessions,
  fetchIssuedTickets,
  fetchMachines,
  fetchEngineHint,
  getOperatorId,
  issueConnectionTicket,
  setOperatorId,
  signalingWsUrl,
  startSession,
  type ConnectionTicket,
  type Machine,
  type EngineHint,
  type Session,
} from "./api";
import { useLocalPeerDisplay } from "./hooks/useLocalPeerDisplay";

const FAVORITES_KEY = "ruc-favorite-machine-ids";

function readFavorites(): Set<number> {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY);
    if (!raw) {
      return new Set();
    }
    const arr = JSON.parse(raw) as number[];
    return new Set(Array.isArray(arr) ? arr : []);
  } catch {
    return new Set();
  }
}

function writeFavorites(ids: Set<number>) {
  localStorage.setItem(FAVORITES_KEY, JSON.stringify([...ids]));
}

type TabId = "recent" | "favorites" | "found" | "address" | "invites";

export function App() {
  const localPeer = useLocalPeerDisplay();
  const [operator, setOperator] = useState(getOperatorId);
  const [authOpen, setAuthOpen] = useState(false);
  const [remoteIdInput, setRemoteIdInput] = useState("");
  const [machines, setMachines] = useState<Machine[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [tickets, setTickets] = useState<ConnectionTicket[]>([]);
  const [favorites, setFavorites] = useState<Set<number>>(readFavorites);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastTicket, setLastTicket] = useState<ConnectionTicket | null>(null);
  const [tab, setTab] = useState<TabId>("recent");
  const [status, setStatus] = useState<"ready" | "busy" | "error">("ready");
  const [engineHint, setEngineHint] = useState<EngineHint | null>(null);
  const [signalStatus, setSignalStatus] = useState<"idle" | "connecting" | "connected" | "closed">("idle");
  const [signalLog, setSignalLog] = useState<string[]>([]);
  const [signalSocket, setSignalSocket] = useState<WebSocket | null>(null);

  const refresh = useCallback(async () => {
    setError(null);
    const [m, s, t] = await Promise.all([fetchMachines(), fetchActiveSessions(), fetchIssuedTickets()]);
    setMachines(m);
    setSessions(s);
    setTickets(t);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setStatus("busy");
      try {
        await refresh();
        if (!cancelled) {
          setStatus("ready");
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
          setStatus("error");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  useEffect(() => {
    fetchEngineHint()
      .then(setEngineHint)
      .catch(() => setEngineHint(null));
  }, []);

  function saveOperator() {
    setOperatorId(operator.trim() || "demo");
    setOperator(getOperatorId());
    setAuthOpen(false);
    void refresh().catch((e) => {
      setError(e instanceof Error ? e.message : String(e));
      setStatus("error");
    });
  }

  function normalizeRemoteId(s: string): string {
    return s.replace(/\s/g, "").trim();
  }

  function findMachineByRemoteInput(input: string): Machine | undefined {
    const key = normalizeRemoteId(input);
    if (!key) {
      return undefined;
    }
    return machines.find((m) => {
      const peer = m.enginePeerId?.replace(/\s/g, "") ?? "";
      return peer && peer === key;
    });
  }

  async function onConnectById() {
    setError(null);
    setLastTicket(null);
    const m = findMachineByRemoteInput(remoteIdInput);
    setStatus("busy");
    try {
      if (m) {
        await startSession(m.id);
        const ticket = await issueConnectionTicket({ machineId: m.id, ttlSeconds: 300 });
        setLastTicket(ticket);
      } else {
        const ticket = await issueConnectionTicket({ remoteId: remoteIdInput, ttlSeconds: 300 });
        setLastTicket(ticket);
      }
      setRemoteIdInput("");
      await refresh();
      setTab("recent");
      setStatus("ready");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStatus("error");
    }
  }

  async function onConnectMachine(machineId: number) {
    setError(null);
    setStatus("busy");
    try {
      await startSession(machineId);
      const ticket = await issueConnectionTicket({ machineId, ttlSeconds: 300 });
      setLastTicket(ticket);
      await refresh();
      setTab("recent");
      setStatus("ready");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStatus("error");
    }
  }

  async function onCloseSession(id: number) {
    setError(null);
    setStatus("busy");
    try {
      await closeSession(id);
      await refresh();
      setStatus("ready");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStatus("error");
    }
  }

  async function onConsumeTicket(token: string) {
    setError(null);
    setStatus("busy");
    try {
      const consumed = await consumeConnectionTicket(token);
      setLastTicket(consumed);
      await refresh();
      setStatus("ready");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setStatus("error");
    }
  }

  function disconnectSignaling() {
    if (signalSocket && signalSocket.readyState === WebSocket.OPEN) {
      signalSocket.close(1000, "manual-close");
    }
    setSignalSocket(null);
    setSignalStatus("closed");
  }

  function connectSignaling(ticket: ConnectionTicket) {
    disconnectSignaling();
    setSignalStatus("connecting");
    const ws = new WebSocket(signalingWsUrl(ticket.token, "viewer", operator || "demo"));

    ws.onopen = () => {
      setSignalStatus("connected");
      setSignalLog((prev) => [`[open] ticket=${ticket.token}`, ...prev].slice(0, 20));
      ws.send(JSON.stringify({ type: "viewer-ready", ts: Date.now() }));
    };
    ws.onmessage = (event) => {
      setSignalLog((prev) => [`[msg] ${String(event.data)}`, ...prev].slice(0, 20));
    };
    ws.onerror = () => {
      setSignalLog((prev) => ["[error] signaling socket error", ...prev].slice(0, 20));
    };
    ws.onclose = (e) => {
      setSignalStatus("closed");
      setSignalLog((prev) => [`[close] code=${e.code} reason=${e.reason || "-"}`, ...prev].slice(0, 20));
      setSignalSocket(null);
    };

    setSignalSocket(ws);
  }

  function toggleFavorite(machineId: number) {
    setFavorites((prev) => {
      const next = new Set(prev);
      if (next.has(machineId)) {
        next.delete(machineId);
      } else {
        next.add(machineId);
      }
      writeFavorites(next);
      return next;
    });
  }

  const favoriteMachines = useMemo(
    () => machines.filter((m) => favorites.has(m.id)),
    [machines, favorites],
  );

  const tabs: { id: TabId; label: string }[] = [
    { id: "recent", label: "РџРѕСЃР»РµРґРЅРёРµ СЃРµР°РЅСЃС‹" },
    { id: "favorites", label: "РР·Р±СЂР°РЅРЅРѕРµ" },
    { id: "found", label: "РќР°Р№РґРµРЅРѕ" },
    { id: "address", label: "РђРґСЂРµСЃРЅР°СЏ РєРЅРёРіР°" },
    { id: "invites", label: "РџСЂРёРіР»Р°С€РµРЅРёСЏ" },
  ];

  return (
    <div className="shell">
      <header className="titlebar">
        <div className="titlebar-drag">
          <span className="titlebar-title">Р РЈРљ РљРѕРЅРЅРµРєС‚</span>
          <span className="titlebar-sub">Р РѕСЃСЃРёР№СЃРєРёР№ СѓРЅРёРІРµСЂСЃРёС‚РµС‚ РєРѕРѕРїРµСЂР°С†РёРё</span>
        </div>
        <div className="titlebar-controls" aria-hidden="true">
          <span className="tb-dot minimize" />
          <span className="tb-dot maximize" />
          <span className="tb-dot close" />
        </div>
      </header>

      <div className="shell-body">
        <aside className="sidebar">
          <div className="brand">
            <div className="brand-mark" aria-hidden="true">
              Р 
            </div>
            <div className="brand-text">
              <span className="brand-name">Р РЈРљ РљРѕРЅРЅРµРєС‚</span>
              <span className="brand-tagline">СѓРґР°Р»С‘РЅРЅС‹Р№ РґРѕСЃС‚СѓРї</span>
            </div>
          </div>

          <p className="sidebar-lead">
            Р’Р°С€ СЂР°Р±РѕС‡РёР№ СЃС‚РѕР» РјРѕР¶РЅРѕ Р·Р°РїСЂРѕСЃРёС‚СЊ РїРѕ СЌС‚РѕРјСѓ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂСѓ Рё РѕРґРЅРѕСЂР°Р·РѕРІРѕРјСѓ РєР»СЋС‡Сѓ РґРѕСЃС‚СѓРїР°.
          </p>

          <div className="sidebar-field">
            <div className="sidebar-label-row">
              <span className="sidebar-label">ID</span>
              <div className="sidebar-icon-btns">
                <button type="button" className="icon-btn" title="РљРѕРїРёСЂРѕРІР°С‚СЊ ID" onClick={() => localPeer.copyId()}>
                  <IconCopy />
                </button>
                <button type="button" className="icon-btn" title="РџР°СЂР°РјРµС‚СЂС‹ (СЃРєРѕСЂРѕ)" disabled>
                  <IconGear />
                </button>
              </div>
            </div>
            <div className="sidebar-value mono">{localPeer.displayId}</div>
          </div>

          <div className="sidebar-field">
            <div className="sidebar-label-row">
              <span className="sidebar-label">РљР»СЋС‡ РґРѕСЃС‚СѓРїР°</span>
              <button type="button" className="icon-btn" title="РќРѕРІС‹Р№ РєР»СЋС‡" onClick={() => localPeer.rotatePassword()}>
                <IconRefresh />
              </button>
            </div>
            <div className="sidebar-value mono">{localPeer.password}</div>
          </div>

          {engineHint ? (
            <div className="sidebar-engine">
              <div className="sidebar-label-row">
                <span className="sidebar-label">Engine relay для демо</span>
              </div>
              <p className="sidebar-mini">
                ID-сервер: <code className="inline-code">{engineHint.idServer}</code>
              </p>
              <p className="sidebar-mini">
                Relay: <code className="inline-code">{engineHint.relayAddress}</code>
              </p>
              <p className="sidebar-mini">
                Ключ: <code className="inline-code">{engineHint.publicKeyFileHint}</code>
              </p>
              <p className="sidebar-mini">
                Шаблон ссылки: <code className="inline-code">{engineHint.deepLinkTemplate || "—"}</code>
              </p>
              <p className="sidebar-mini">Peer в книге: {engineHint.peerIds.join(", ")}</p>
              <ol className="sidebar-steps">
                {engineHint.steps.map((step, i) => (
                  <li key={i}>{step}</li>
                ))}
              </ol>
            </div>
          ) : (
            <p className="sidebar-mini muted">Подсказка по engine relay: запустите бэкенд.</p>
          )}

          <div className="sidebar-spacer" />

          <div className="sidebar-support">
            <p className="sidebar-support-text">Р•СЃС‚СЊ РІРѕРїСЂРѕСЃС‹ РёР»Рё РЅР°С€Р»Рё РѕС€РёР±РєСѓ?</p>
            <a className="btn-outline" href="mailto:support@ruc.ru">
              РЎР»СѓР¶Р±Р° РїРѕРґРґРµСЂР¶РєРё
            </a>
          </div>
        </aside>

        <main className="workspace">
          <div className="workspace-top">
            <section className="card card-wide">
              <h2 className="card-title">РЈРїСЂР°РІР»РµРЅРёРµ СѓРґР°Р»С‘РЅРЅС‹Рј СЂР°Р±РѕС‡РёРј СЃС‚РѕР»РѕРј</h2>
              <div className="connect-row">
                <input
                  className="field-lg"
                  type="text"
                  value={remoteIdInput}
                  onChange={(e) => setRemoteIdInput(e.target.value)}
                  placeholder={
                    engineHint?.peerIds?.length
                      ? `Например ${engineHint.peerIds[0]} (колонка «Peer»)`
                      : "Введите удалённый ID (колонка «Peer»)"
                  }
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      void onConnectById();
                    }
                  }}
                />
                <div className="connect-actions">
                  <button type="button" className="btn-primary" onClick={() => void onConnectById()}>
                    РџРѕРґРєР»СЋС‡РёС‚СЊСЃСЏ
                  </button>
                  <button type="button" className="btn-primary-chevron" title="Р”РѕРї. РґРµР№СЃС‚РІРёСЏ (СЃРєРѕСЂРѕ)" disabled>
                    в–ѕ
                  </button>
                </div>
              </div>
              <ol className="demo-steps">
                <li>
                  РќР° СЃРІРѕС‘Рј РџРљ СѓСЃС‚Р°РЅРѕРІРёС‚Рµ С‚РѕС‚ Р¶Рµ РєР»РёРµРЅС‚ СѓРґР°Р»С‘РЅРЅРѕРіРѕ РґРѕСЃС‚СѓРїР°, С‡С‚Рѕ Рё РЅР° СѓРґР°Р»С‘РЅРЅРѕР№ РјР°С€РёРЅРµ (РґР»СЏ РґРµРјРѕ вЂ”
                  клиент удалённого доступа\.
                </li>
                <li>
                  Р’РІРµРґРёС‚Рµ Р»СЋР±РѕР№ ID СЃРІРµСЂС…Сѓ Рё РЅР°Р¶РјРёС‚Рµ В«РџРѕРґРєР»СЋС‡РёС‚СЊСЃСЏВ», Р»РёР±Рѕ РІС‹Р±РµСЂРёС‚Рµ РјР°С€РёРЅСѓ РІ В«РђРґСЂРµСЃРЅРѕР№ РєРЅРёРіРµВ».
                </li>
              </ol>
            </section>

            <section className="card card-narrow">
              <div className="card-title-row">
                <h2 className="card-title">РђРІС‚РѕСЂРёР·Р°С†РёСЏ</h2>
                <span className="info-dot" title="Р’С…РѕРґ РІ РєРѕРЅС‚СѓСЂ Р РЈРљ">
                  <IconInfo />
                </span>
              </div>
              <p className="card-muted">РђРІС‚РѕСЂРёР·СѓР№С‚РµСЃСЊ РґР»СЏ СЂР°Р±РѕС‚С‹ СЃ Р°РґСЂРµСЃРЅРѕР№ РєРЅРёРіРѕР№ Рё СЃРµСЃСЃРёСЏРјРё.</p>
              {!authOpen ? (
                <button type="button" className="btn-primary btn-block" onClick={() => setAuthOpen(true)}>
                  <IconLogin />
                  Р’РѕР№С‚Рё
                </button>
              ) : (
                <div className="auth-form">
                  <label className="stack-label">
                    Р›РѕРіРёРЅ (Р·Р°РіРѕР»РѕРІРѕРє X-Ruc-User)
                    <input
                      className="field-md"
                      type="text"
                      value={operator}
                      onChange={(e) => setOperator(e.target.value)}
                      placeholder="ivanov"
                    />
                  </label>
                  <div className="auth-form-actions">
                    <button type="button" className="btn-primary" onClick={saveOperator}>
                      РЎРѕС…СЂР°РЅРёС‚СЊ
                    </button>
                    <button type="button" className="btn-ghost" onClick={() => setAuthOpen(false)}>
                      РћС‚РјРµРЅР°
                    </button>
                  </div>
                </div>
              )}
            </section>
          </div>

          {error ? <div className="banner-error">{error}</div> : null}
          {lastTicket ? (
            <div className="banner-success">
              <strong>РўРёРєРµС‚ РїРѕРґРєР»СЋС‡РµРЅРёСЏ РІС‹РґР°РЅ: {lastTicket.token}</strong>
              <p className="muted">
                ID: {lastTicket.remoteId}, РґРµР№СЃС‚РІСѓРµС‚ РґРѕ {new Date(lastTicket.expiresAt).toLocaleTimeString("ru-RU")}
              </p>
              <p className="muted">{lastTicket.connectionHint}</p>
              {lastTicket.deepLink ? (
                <div className="deeplink-actions">
                  <a className="btn-primary btn-sm" href={lastTicket.deepLink}>
                    РћС‚РєСЂС‹С‚СЊ РІ РєР»РёРµРЅС‚Рµ
                  </a>
                  <button
                    type="button"
                    className="btn-secondary-outline btn-sm"
                    onClick={() => void navigator.clipboard.writeText(lastTicket.deepLink!)}
                  >
                    РљРѕРїРёСЂРѕРІР°С‚СЊ СЃСЃС‹Р»РєСѓ
                  </button>
                </div>
              ) : (
                <p className="muted small">РќРµ РЅР°СЃС‚СЂРѕРµРЅ С€Р°Р±Р»РѕРЅ deep link РЅР° СЃРµСЂРІРµСЂРµ.</p>
              )}
            </div>
          ) : null}

          {tickets.length > 0 ? (
            <div className="panel-inline">
              <div className="panel-inline-head">
                <strong>Р’С‹РґР°РЅРЅС‹Рµ С‚РёРєРµС‚С‹ ({tickets.length})</strong>
                <span className="muted">TTL РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ: 5 РјРёРЅСѓС‚</span>
              </div>
              <div className="ticket-list">
                {tickets.slice(0, 5).map((t) => (
                  <div key={t.token} className="ticket-row">
                    <div className="ticket-main">
                      <span className="ticket-id mono">{t.remoteId}</span>
                      <span className="muted small">
                        token: {t.token} В· РґРѕ {new Date(t.expiresAt).toLocaleTimeString("ru-RU")}
                      </span>
                    </div>
                    <div className="ticket-actions">
                      {t.deepLink ? (
                        <a className="btn-primary btn-sm" href={t.deepLink}>
                          РћС‚РєСЂС‹С‚СЊ
                        </a>
                      ) : null}
                      <button type="button" className="btn-secondary-outline btn-sm" onClick={() => void onConsumeTicket(t.token)}>
                        Consume
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {lastTicket ? (
            <div className="panel-inline">
              <div className="panel-inline-head">
                <strong>Signaling РєР°РЅР°Р»</strong>
                <span className="muted">СЃС‚Р°С‚СѓСЃ: {signalStatus}</span>
              </div>
              <div className="ticket-actions">
                <button
                  type="button"
                  className="btn-primary btn-sm"
                  onClick={() => connectSignaling(lastTicket)}
                  disabled={signalStatus === "connecting"}
                >
                  РџРѕРґРєР»СЋС‡РёС‚СЊ signaling
                </button>
                <button type="button" className="btn-secondary-outline btn-sm" onClick={disconnectSignaling}>
                  РћС‚РєР»СЋС‡РёС‚СЊ
                </button>
              </div>
              {signalLog.length > 0 ? (
                <pre className="signal-log">{signalLog.join("\n")}</pre>
              ) : (
                <p className="muted small">РџРѕСЃР»Рµ РїРѕРґРєР»СЋС‡РµРЅРёСЏ Р·РґРµСЃСЊ Р±СѓРґСѓС‚ ACK/СЃРѕРѕР±С‰РµРЅРёСЏ РѕС‚ agent-side signaling.</p>
              )}
            </div>
          ) : null}

          <nav className="tabs" role="tablist">
            {tabs.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                className={`tab ${tab === t.id ? "tab-active" : ""}`}
                onClick={() => setTab(t.id)}
              >
                {t.label}
              </button>
            ))}
          </nav>

          <div className="workspace-pane" role="tabpanel">
            {tab === "recent" && (
              <div className="pane-inner">
                {loading ? (
                  <p className="muted">Р—Р°РіСЂСѓР·РєР°вЂ¦</p>
                ) : sessions.length === 0 ? (
                  <div className="empty-state">
                    <p>РќРµС‚ Р°РєС‚РёРІРЅС‹С… СЃРµР°РЅСЃРѕРІ.</p>
                    <p className="muted">РџРѕРґРєР»СЋС‡РёС‚РµСЃСЊ РїРѕ ID РёР»Рё РІС‹Р±РµСЂРёС‚Рµ РјР°С€РёРЅСѓ РІ Р°РґСЂРµСЃРЅРѕР№ РєРЅРёРіРµ.</p>
                  </div>
                ) : (
                  <ul className="session-list">
                    {sessions.map((s) => (
                      <li key={s.id} className="session-row">
                        <div className="session-main">
                          <span className="session-title">
                            {s.roomCode} В· {s.hostname}
                          </span>
                          <span className="session-meta">
                            СЃ {new Date(s.startedAt).toLocaleString("ru-RU")} В· РѕРїРµСЂР°С‚РѕСЂ {s.operatorUserId}
                          </span>
                          <div className="session-deeplink">
                            <p className="session-hint">{s.connectionHint}</p>
                            {s.deepLink ? (
                              <>
                                <code className="deeplink-code">{s.deepLink}</code>
                                <div className="deeplink-actions">
                                  <a className="btn-primary btn-sm" href={s.deepLink}>
                                    РћС‚РєСЂС‹С‚СЊ РІ РєР»РёРµРЅС‚Рµ
                                  </a>
                                  <button
                                    type="button"
                                    className="btn-secondary-outline btn-sm"
                                    onClick={() => void navigator.clipboard.writeText(s.deepLink!)}
                                  >
                                    РљРѕРїРёСЂРѕРІР°С‚СЊ СЃСЃС‹Р»РєСѓ
                                  </button>
                                </div>
                              </>
                            ) : (
                              <p className="muted small">РЎСЃС‹Р»РєР° РЅРµРґРѕСЃС‚СѓРїРЅР°: Р·Р°РґР°Р№С‚Рµ peer РІ Р°РґСЂРµСЃРЅРѕР№ РєРЅРёРіРµ Рё С€Р°Р±Р»РѕРЅ deep-link РЅР° СЃРµСЂРІРµСЂРµ.</p>
                            )}
                          </div>
                        </div>
                        <button type="button" className="btn-danger-outline" onClick={() => void onCloseSession(s.id)}>
                          Р—Р°РІРµСЂС€РёС‚СЊ
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {tab === "favorites" && (
              <div className="pane-inner">
                {favoriteMachines.length === 0 ? (
                  <div className="empty-state">
                    <p>РР·Р±СЂР°РЅРЅС‹С… РјР°С€РёРЅ РїРѕРєР° РЅРµС‚.</p>
                    <p className="muted">РћС‚РєСЂРѕР№С‚Рµ В«РђРґСЂРµСЃРЅР°СЏ РєРЅРёРіР°В» Рё РѕС‚РјРµС‚СЊС‚Рµ Р·РІС‘Р·РґРѕС‡РєРѕР№ РЅСѓР¶РЅС‹Рµ РџРљ.</p>
                  </div>
                ) : (
                  <MachineTable
                    rows={favoriteMachines}
                    favorites={favorites}
                    onToggleFavorite={toggleFavorite}
                    onConnect={onConnectMachine}
                  />
                )}
              </div>
            )}

            {tab === "found" && (
              <div className="pane-inner empty-state">
                <p>РџРѕРёСЃРє РїРѕ СЃРµС‚Рё Р РЈРљ</p>
                <p className="muted">Р—РґРµСЃСЊ РїРѕСЏРІСЏС‚СЃСЏ РѕР±РЅР°СЂСѓР¶РµРЅРЅС‹Рµ СѓР·Р»С‹, РєРѕРіРґР° Р±СѓРґРµС‚ РїРѕРґРєР»СЋС‡С‘РЅ РјРѕРґСѓР»СЊ СЃРєР°РЅРёСЂРѕРІР°РЅРёСЏ.</p>
              </div>
            )}

            {tab === "address" && (
              <div className="pane-inner">
                {loading ? (
                  <p className="muted">Р—Р°РіСЂСѓР·РєР°вЂ¦</p>
                ) : (
                  <MachineTable
                    rows={machines}
                    favorites={favorites}
                    onToggleFavorite={toggleFavorite}
                    onConnect={onConnectMachine}
                  />
                )}
              </div>
            )}

            {tab === "invites" && (
              <div className="pane-inner empty-state">
                <p>РџСЂРёРіР»Р°С€РµРЅРёСЏ</p>
                <p className="muted">Р’С…РѕРґСЏС‰РёРµ Р·Р°РїСЂРѕСЃС‹ РЅР° РґРѕСЃС‚СѓРї Рє РІР°С€РµРјСѓ СЃС‚РѕР»Сѓ вЂ” РІ СЃР»РµРґСѓСЋС‰РµР№ РІРµСЂСЃРёРё.</p>
              </div>
            )}
          </div>

          <footer className="statusbar">
            <span className={`status-dot ${status === "ready" ? "ok" : status === "busy" ? "busy" : "err"}`} />
            <span className="status-text">
              {status === "ready" && "Р“РѕС‚РѕРІРѕ"}
              {status === "busy" && "Р’С‹РїРѕР»РЅСЏРµС‚СЃСЏвЂ¦"}
              {status === "error" && "РћС€РёР±РєР°"}
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}

function MachineTable({
  rows,
  favorites,
  onToggleFavorite,
  onConnect,
}: {
  rows: Machine[];
  favorites: Set<number>;
  onToggleFavorite: (id: number) => void;
  onConnect: (id: number) => void;
}) {
  if (rows.length === 0) {
    return (
      <div className="empty-state">
        <p>РЎРїРёСЃРѕРє РїСѓСЃС‚.</p>
      </div>
    );
  }
  return (
    <>
      <p className="table-caption muted">
        Р”Р»СЏ РґРµРјРѕ: Р·РЅР°С‡РµРЅРёРµ В«PeerВ» = ID СѓРґР°Р»С‘РЅРЅРѕР№ РјР°С€РёРЅС‹ РІ РєР»РёРµРЅС‚Рµ. РџСЂРёРјРµСЂ Р·Р°РїРёСЃРµР№ РїРѕСЃР»Рµ РїРµСЂРІРѕРіРѕ Р·Р°РїСѓСЃРєР°:{" "}
        <strong>111222333</strong>, <strong>444555666</strong> (РїСЂРё СЃРјРµРЅРµ Р‘Р” РїРµСЂРµСЃРѕР·РґР°Р№С‚Рµ РґР°РЅРЅС‹Рµ РёР»Рё РѕР±РЅРѕРІРёС‚Рµ peer РІСЂСѓС‡РЅСѓСЋ).
      </p>
      <table className="data-table">
        <thead>
          <tr>
            <th className="col-star" />
            <th>РђСѓРґРёС‚РѕСЂРёСЏ / РјРµСЃС‚Рѕ</th>
            <th>РРјСЏ РџРљ</th>
            <th>Peer (ID РІ РєР»РёРµРЅС‚Рµ)</th>
            <th />
          </tr>
        </thead>
        <tbody>
        {rows.map((m) => (
          <tr key={m.id}>
            <td>
              <button
                type="button"
                className={`star-btn ${favorites.has(m.id) ? "on" : ""}`}
                title={favorites.has(m.id) ? "РЈР±СЂР°С‚СЊ РёР· РёР·Р±СЂР°РЅРЅРѕРіРѕ" : "Р’ РёР·Р±СЂР°РЅРЅРѕРµ"}
                onClick={() => onToggleFavorite(m.id)}
              >
                в…
              </button>
            </td>
            <td>{m.roomCode}</td>
            <td>{m.hostname}</td>
            <td>
              <code className="cell-mono">{m.enginePeerId || "вЂ”"}</code>
            </td>
            <td className="cell-actions">
              <button type="button" className="btn-primary btn-sm" onClick={() => void onConnect(m.id)}>
                РџРѕРґРєР»СЋС‡РёС‚СЊСЃСЏ
              </button>
            </td>
          </tr>
        ))}
        </tbody>
      </table>
    </>
  );
}

function IconCopy() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="9" y="9" width="13" height="13" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>
  );
}

function IconGear() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
    </svg>
  );
}

function IconRefresh() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M23 4v6h-6M1 20v-6h6" />
      <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
    </svg>
  );
}

function IconInfo() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4M12 8h.01" />
    </svg>
  );
}

function IconLogin() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3" />
    </svg>
  );
}

