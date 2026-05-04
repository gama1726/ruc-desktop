# Agent Signaling Client (MVP)

`ru.ruc.desktop.engine.AgentSignalingClient` is a minimal host-side client for Sprint 1.

What it does:

- sends heartbeat to `/api/agents/heartbeat`
- polls `/api/tickets/pull?remoteId=...`
- joins `/ws/signaling` as `role=agent`
- responds to signaling typed messages (`offer`, `answer`, `ice-candidate`)
- optionally forwards signaling payloads into a native C++ helper process

## Run from IntelliJ

Run class:

- `ru.ruc.desktop.engine.AgentSignalingClient`

## Environment variables

- `RUC_BACKEND_BASE_URL` (default: `http://localhost:8080`)
- `RUC_AGENT_UID` (default: `agent-local-1`)
- `RUC_AGENT_DISPLAY_NAME` (default: `RUC Agent Local`)
- `RUC_AGENT_REMOTE_ID` (default: `260227322`)
- `RUC_AGENT_MACHINE_ID` (optional Long)
- `RUC_AGENT_IP` (default: `127.0.0.1`)
- `RUC_AGENT_HEARTBEAT_SECONDS` (default: `15`)
- `RUC_AGENT_HELPER_PATH` (optional absolute path to `ruc_native_helper.exe`)

## Quick local test

1. Start backend.
2. Run `AgentSignalingClient` with `RUC_AGENT_REMOTE_ID` matching the target ID.
3. In frontend issue a ticket for the same ID.
4. Click `Подключить signaling` and watch logs in UI + agent stdout.

## Native helper (C++) integration

If `RUC_AGENT_HELPER_PATH` is set, `AgentSignalingClient` starts helper process
and exchanges JSON-line frames over `stdin/stdout`.

If helper is not configured or unavailable, agent keeps working in mock mode.
