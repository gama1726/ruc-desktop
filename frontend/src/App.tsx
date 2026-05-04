import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
import {
  closeSession,
  fetchActiveSessions,
  fetchMachines,
  getOperatorId,
  setOperatorId,
  startSession,
  type Machine,
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
  const [favorites, setFavorites] = useState<Set<number>>(readFavorites);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastStarted, setLastStarted] = useState<Session | null>(null);
  const [tab, setTab] = useState<TabId>("recent");
  const [status, setStatus] = useState<"ready" | "busy" | "error">("ready");

  const refresh = useCallback(async () => {
    setError(null);
    const [m, s] = await Promise.all([fetchMachines(), fetchActiveSessions()]);
    setMachines(m);
    setSessions(s);
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
    const m = findMachineByRemoteInput(remoteIdInput);
    if (!m) {
      setError("Удалённый ID не найден в адресной книге. Проверьте номер или откройте вкладку «Адресная книга».");
      setStatus("error");
      return;
    }
    setStatus("busy");
    try {
      const s = await startSession(m.id);
      setLastStarted(s);
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
      const s = await startSession(machineId);
      setLastStarted(s);
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
                  placeholder="Введите удалённый ID"
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
                            {s.roomCode} · {s.hostname}
                          </span>
                          <span className="session-meta">
                            с {new Date(s.startedAt).toLocaleString("ru-RU")} · оператор {s.operatorUserId}
                          </span>
                          {lastStarted?.id === s.id && lastStarted.deepLink ? (
                            <div className="session-deeplink">
                              <span className="muted">{lastStarted.connectionHint}</span>
                              <code className="deeplink-code">{lastStarted.deepLink}</code>
                              <button
                                type="button"
                                className="btn-ghost btn-sm"
                                onClick={() => void navigator.clipboard.writeText(lastStarted.deepLink!)}
                              >
                                Копировать ссылку
                              </button>
                            </div>
                          ) : null}
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
    <table className="data-table">
      <thead>
        <tr>
          <th className="col-star" />
          <th>Аудитория</th>
          <th>Hostname</th>
          <th>Peer (движок)</th>
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
                ★
              </button>
            </td>
            <td>{m.roomCode}</td>
            <td>{m.hostname}</td>
            <td>
              <code className="cell-mono">{m.enginePeerId || "—"}</code>
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
