import { useCallback, useEffect, useState } from "react";
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

export function App() {
  const [operator, setOperator] = useState(getOperatorId);
  const [machines, setMachines] = useState<Machine[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastStarted, setLastStarted] = useState<Session | null>(null);

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
      try {
        await refresh();
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
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
    void refresh().catch((e) => setError(e instanceof Error ? e.message : String(e)));
  }

  async function onConnect(machineId: number) {
    setError(null);
    try {
      const s = await startSession(machineId);
      setLastStarted(s);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function onCloseSession(id: number) {
    setError(null);
    try {
      await closeSession(id);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <div className="app">
      <h1>РУК — оркестрация удалённого доступа</h1>
      <p className="sub">
        Собственный бэкенд и интерфейс; подключение к рабочему столу выполняет установленный у вас клиент
        движка (шаблон ссылки задаётся на сервере).
      </p>

      <div className="panel">
        <h2>Оператор</h2>
        <p className="hint">
          Заголовок <span className="mono">X-Ruc-User</span> — заглушка до интеграции с вашим IdP. Значение
          сохраняется в браузере.
        </p>
        <div className="row">
          <label>
            Идентификатор (логин)
            <input
              type="text"
              value={operator}
              onChange={(e) => setOperator(e.target.value)}
              placeholder="ivanov"
            />
          </label>
          <button type="button" className="secondary" onClick={saveOperator}>
            Применить
          </button>
        </div>
      </div>

      {error ? <p className="err">{error}</p> : null}

      <div className="panel">
        <h2>Активные сессии</h2>
        {loading ? (
          <p className="hint">Загрузка…</p>
        ) : sessions.length === 0 ? (
          <p className="hint">Нет активных сессий для текущего оператора.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Аудитория</th>
                <th>Хост</th>
                <th>Начало</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td>
                  <td>{s.roomCode}</td>
                  <td>{s.hostname}</td>
                  <td>{new Date(s.startedAt).toLocaleString()}</td>
                  <td>
                    <button type="button" className="danger" onClick={() => onCloseSession(s.id)}>
                      Завершить
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="panel">
        <h2>Машины</h2>
        {loading ? (
          <p className="hint">Загрузка…</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Аудитория</th>
                <th>Hostname</th>
                <th>Peer (движок)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {machines.map((m) => (
                <tr key={m.id}>
                  <td>{m.roomCode}</td>
                  <td>{m.hostname}</td>
                  <td className="mono">{m.enginePeerId || "—"}</td>
                  <td>
                    <button type="button" onClick={() => onConnect(m.id)}>
                      Начать сессию
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {lastStarted ? (
        <div className="panel">
          <h2>Последняя созданная сессия</h2>
          <p className="hint">{lastStarted.connectionHint}</p>
          {lastStarted.deepLink ? (
            <>
              <p className="hint">Deep link (проверьте схему под ваш клиент):</p>
              <div className="mono">{lastStarted.deepLink}</div>
              <div className="actions">
                <button
                  type="button"
                  className="secondary"
                  onClick={() => void navigator.clipboard.writeText(lastStarted.deepLink!)}
                >
                  Копировать ссылку
                </button>
              </div>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
