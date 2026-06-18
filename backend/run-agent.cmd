@echo off
cd /d "%~dp0"

set RUC_BACKEND_BASE_URL=http://localhost:8080
set RUC_AGENT_REMOTE_ID=260227322
set RUC_AGENT_SCREEN_CAPTURE=true
set RUC_AGENT_CAPTURE_MAX_WIDTH=800
set RUC_AGENT_CAPTURE_INTERVAL_MS=700
set RUC_AGENT_CAPTURE_JPEG_QUALITY=0.45

set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if not exist %MVN% (
  echo Maven not found. Install Maven or fix MVN path in run-agent.cmd
  exit /b 1
)

echo Starting RUC Agent (same PC demo)...
echo Backend: %RUC_BACKEND_BASE_URL%
echo Remote ID: %RUC_AGENT_REMOTE_ID%
echo.

%MVN% -DskipTests compile exec:java "-Dexec.mainClass=ru.ruc.desktop.engine.AgentSignalingClient"
pause
