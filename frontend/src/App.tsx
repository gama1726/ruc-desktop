import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  startSession,
  type ConnectionTicket,
  type Machine,
  type EngineHint,
  type Session,
} from "./api";
import { useLocalPeerDisplay } from "./hooks/useLocalPeerDisplay";
import { ViewerMediaClient } from "./mediaStream";
import { ViewerSignalingClient } from "./signaling";

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
  const [mediaStatus, setMediaStatus] = useState<"idle" | "connecting" | "connected" | "closed">("idle");
  const [remoteFrameUrl, setRemoteFrameUrl] = useState<string | null>(null);
  const [webrtcVideoActive, setWebrtcVideoActive] = useState(false);
  const [mediaLog, setMediaLog] = useState<string[]>([]);
  const signalingRef = useRef<ViewerSignalingClient | null>(null);
  const mediaRef = useRef<ViewerMediaClient | null>(null);

  useEffect(() => {
    signalingRef.current = new ViewerSignalingClient({
      onStatus: (next) => setSignalStatus(next),
      onLog: (line) => setSignalLog((prev) => [line, ...prev].slice(0, 20)),
      onRemoteVideoStream: (stream) => {
        setWebrtcVideoActive(true);
        const video = document.getElementById("ruc-remote-video") as HTMLVideoElement | null;
        if (video) {
          video.srcObject = stream;
          void video.play().catch(() => undefined);
        }
      },
    });
    mediaRef.current = new ViewerMediaClient({
      onStatus: (next) => setMediaStatus(next),
      onLog: (line) => setMediaLog((prev) => [line, ...prev].slice(0, 10)),
      onFrame: (payload) => {
        if (payload.data) {
          const mime = payload.mime || "image/jpeg";
          setRemoteFrameUrl(`data:${mime};base64,${payload.data}`);
        }
      },
    });
    return () => {
      signalingRef.current?.disconnect();
      signalingRef.current = null;
      mediaRef.current?.disconnect();
      mediaRef.current = null;
    };
  }, []);

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
    signalingRef.current?.disconnect();
    mediaRef.current?.disconnect();
    setRemoteFrameUrl(null);
    setWebrtcVideoActive(false);
  }

  function connectSignaling(ticket: ConnectionTicket) {
    setWebrtcVideoActive(false);
    signalingRef.current?.connect(ticket, operator || "demo");
  }

  function connectMediaFallback(ticket: ConnectionTicket) {
    mediaRef.current?.connect(ticket.token);
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
    { id: "recent", label: "Последние сеансы" },
    { id: "favorites", label: "Избранное" },
    { id: "found", label: "Найдено" },
    { id: "address", label: "Адресная книга" },
    { id: "invites", label: "Приглашения" },
  ];

  return (
    <div className="shell">
      <header className="titlebar">
        <div className="titlebar-drag">
          <span className="titlebar-title">РУК Коннект</span>
          <span className="titlebar-sub">Российский университет кооперации</span>
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
              <span className="brand-name">РУК Коннект</span>
              <span className="brand-tagline">удалённый доступ</span>
            </div>
          </div>

          <p className="sidebar-lead">
            Ваш рабочий стол можно запросить по этому идентификатору и одноразовому ключу доступа.
          </p>

          <div className="sidebar-field">
            <div className="sidebar-label-row">
              <span className="sidebar-label">ID</span>
              <div className="sidebar-icon-btns">
                <button type="button" className="icon-btn" title="Копировать ID" onClick={() => localPeer.copyId()}>
                  <IconCopy />
                </button>
                <button type="button" className="icon-btn" title="Параметры (скоро)" disabled>
                  <IconGear />
                </button>
              </div>
            </div>
            <div className="sidebar-value mono">{localPeer.displayId}</div>
          </div>

          <div className="sidebar-field">
            <div className="sidebar-label-row">
              <span className="sidebar-label">Ключ доступа</span>
              <button type="button" className="icon-btn" title="Новый ключ" onClick={() => localPeer.rotatePassword()}>
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
            <p className="sidebar-support-text">Есть вопросы или нашли ошибку?</p>
            <a className="btn-outline" href="mailto:support@ruc.ru">
              Служба поддержки
            </a>
          </div>
        </aside>

        <main className="workspace">
          <div className="workspace-top">
            <section className="card card-wide">
              <h2 className="card-title">Управление удалённым рабочим столом</h2>
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
                    Подключиться
                  </button>
                  <button type="button" className="btn-primary-chevron" title="Доп. действия (скоро)" disabled>
                    ▾
                  </button>
                </div>
              </div>
              <ol className="demo-steps">
                <li>
                  На своём ПК установите тот же клиент удалённого доступа, что и на удалённой машине (для демо —
                  клиент удалённого доступа\.
                </li>
                <li>
                  Введите любой ID сверху и нажмите «Подключиться», либо выберите машину в «Адресной книге».
                </li>
              </ol>
            </section>

            <section className="card card-narrow">
              <div className="card-title-row">
                <h2 className="card-title">Авторизация</h2>
                <span className="info-dot" title="Вход в контур РУК">
                  <IconInfo />
                </span>
              </div>
              <p className="card-muted">Авторизуйтесь для работы с адресной книгой и сессиями.</p>
              {!authOpen ? (
                <button type="button" className="btn-primary btn-block" onClick={() => setAuthOpen(true)}>
                  <IconLogin />
                  Войти
                </button>
              ) : (
                <div className="auth-form">
                  <label className="stack-label">
                    Логин (заголовок X-Ruc-User)
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
                      Сохранить
                    </button>
                    <button type="button" className="btn-ghost" onClick={() => setAuthOpen(false)}>
                      Отмена
                    </button>
                  </div>
                </div>
              )}
            </section>
          </div>

          {error ? <div className="banner-error">{error}</div> : null}
          {lastTicket ? (
            <div className="banner-success">
              <strong>Тикет подключения выдан: {lastTicket.token}</strong>
              <p className="muted">
                ID: {lastTicket.remoteId}, действует до {new Date(lastTicket.expiresAt).toLocaleTimeString("ru-RU")}
              </p>
              <p className="muted">{lastTicket.connectionHint}</p>
              {lastTicket.deepLink ? (
                <div className="deeplink-actions">
                  <a className="btn-primary btn-sm" href={lastTicket.deepLink}>
                    Открыть в клиенте
                  </a>
                  <button
                    type="button"
                    className="btn-secondary-outline btn-sm"
                    onClick={() => void navigator.clipboard.writeText(lastTicket.deepLink!)}
                  >
                    Копировать ссылку
                  </button>
                </div>
              ) : (
                <p className="muted small">Не настроен шаблон deep link на сервере.</p>
              )}
            </div>
          ) : null}

          {tickets.length > 0 ? (
            <div className="panel-inline">
              <div className="panel-inline-head">
                <strong>Выданные тикеты ({tickets.length})</strong>
                <span className="muted">TTL по умолчанию: 5 минут</span>
              </div>
              <div className="ticket-list">
                {tickets.slice(0, 5).map((t) => (
                  <div key={t.token} className="ticket-row">
                    <div className="ticket-main">
                      <span className="ticket-id mono">{t.remoteId}</span>
                      <span className="muted small">
                        token: {t.token} · до {new Date(t.expiresAt).toLocaleTimeString("ru-RU")}
                      </span>
                    </div>
                    <div className="ticket-actions">
                      {t.deepLink ? (
                        <a className="btn-primary btn-sm" href={t.deepLink}>
                          Открыть
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
                <strong>Signaling канал</strong>
                <span className="muted">статус: {signalStatus}</span>
              </div>
              <div className="ticket-actions">
                <button
                  type="button"
                  className="btn-primary btn-sm"
                  onClick={() => connectSignaling(lastTicket)}
                  disabled={signalStatus === "connecting"}
                >
                  Подключить signaling
                </button>
                <button type="button" className="btn-secondary-outline btn-sm" onClick={disconnectSignaling}>
                  Отключить
                </button>
              </div>
              {signalLog.length > 0 ? (
                <pre className="signal-log">{signalLog.join("\n")}</pre>
              ) : (
                <p className="muted small">После подключения здесь будут ACK/сообщения от agent-side signaling.</p>
              )}
            </div>
          ) : null}

          {lastTicket ? (
            <div className="panel-inline">
              <div className="panel-inline-head">
                <strong>Медиа-канал (экран агента)</strong>
                <span className="muted">
                  WebRTC: {webrtcVideoActive ? "видео" : signalStatus === "connected" ? "ожидание" : "—"} · JPEG: {mediaStatus}
                </span>
              </div>
              <p className="muted small">
                Основной режим — WebRTC-видео с агента (захват экрана). JPEG через /ws/media — запасной вариант при отладке.
              </p>
              <div className="ticket-actions">
                <button
                  type="button"
                  className="btn-secondary-outline btn-sm"
                  onClick={() => lastTicket && connectMediaFallback(lastTicket)}
                  disabled={mediaStatus === "connecting"}
                >
                  Подключить JPEG fallback
                </button>
              </div>
              <div className={`remote-desktop-view${webrtcVideoActive ? " remote-desktop-view--webrtc" : ""}`}>
                {remoteFrameUrl && !webrtcVideoActive ? (
                  <img className="remote-desktop-img" src={remoteFrameUrl} alt="Удалённый рабочий стол" />
                ) : !webrtcVideoActive ? (
                  <div className="remote-desktop-placeholder">Ожидание WebRTC-видео от агента…</div>
                ) : null}
                <video id="ruc-remote-video" className="remote-desktop-video" autoPlay playsInline muted />
              </div>
              {mediaLog.length > 0 ? <pre className="signal-log">{mediaLog.join("\n")}</pre> : null}
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
                  <p className="muted">Загрузка…</p>
                ) : sessions.length === 0 ? (
                  <div className="empty-state">
                    <p>Нет активных сеансов.</p>
                    <p className="muted">Подключитесь по ID или выберите машину в адресной книге.</p>
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
                            с {new Date(s.startedAt).toLocaleString("ru-RU")} · оператор {s.operatorUserId}
                          </span>
                          <div className="session-deeplink">
                            <p className="session-hint">{s.connectionHint}</p>
                            {s.deepLink ? (
                              <>
                                <code className="deeplink-code">{s.deepLink}</code>
                                <div className="deeplink-actions">
                                  <a className="btn-primary btn-sm" href={s.deepLink}>
                                    Открыть в клиенте
                                  </a>
                                  <button
                                    type="button"
                                    className="btn-secondary-outline btn-sm"
                                    onClick={() => void navigator.clipboard.writeText(s.deepLink!)}
                                  >
                                    Копировать ссылку
                                  </button>
                                </div>
                              </>
                            ) : (
                              <p className="muted small">Ссылка недоступна: задайте peer в адресной книге и шаблон deep-link на сервере.</p>
                            )}
                          </div>
                        </div>
                        <button type="button" className="btn-danger-outline" onClick={() => void onCloseSession(s.id)}>
                          Завершить
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
                    <p>Избранных машин пока нет.</p>
                    <p className="muted">Откройте «Адресная книга» и отметьте звёздочкой нужные ПК.</p>
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
                <p>Поиск по сети РУК</p>
                <p className="muted">Здесь появятся обнаруженные узлы, когда будет подключён модуль сканирования.</p>
              </div>
            )}

            {tab === "address" && (
              <div className="pane-inner">
                {loading ? (
                  <p className="muted">Загрузка…</p>
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
                <p>Приглашения</p>
                <p className="muted">Входящие запросы на доступ к вашему столу — в следующей версии.</p>
              </div>
            )}
          </div>

          <footer className="statusbar">
            <span className={`status-dot ${status === "ready" ? "ok" : status === "busy" ? "busy" : "err"}`} />
            <span className="status-text">
              {status === "ready" && "Готово"}
              {status === "busy" && "Выполняется…"}
              {status === "error" && "Ошибка"}
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
        <p>Список пуст.</p>
      </div>
    );
  }
  return (
    <>
      <p className="table-caption muted">
        Для демо: значение «Peer» = ID удалённой машины в клиенте. Пример записей после первого запуска:{" "}
        <strong>111222333</strong>, <strong>444555666</strong> (при смене БД пересоздайте данные или обновите peer вручную).
      </p>
      <table className="data-table">
        <thead>
          <tr>
            <th className="col-star" />
            <th>Аудитория / место</th>
            <th>Имя ПК</th>
            <th>Peer (ID в клиенте)</th>
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
                title={favorites.has(m.id) ? "Убрать из избранного" : "В избранное"}
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
                Подключиться
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

