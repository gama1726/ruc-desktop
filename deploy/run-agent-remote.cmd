@echo off
REM Агент на удалённом ПК — подставьте IP или домен сервера с РУК Коннект
set RUC_BACKEND_BASE_URL=http://192.168.1.100
set RUC_AGENT_REMOTE_ID=260227322
set RUC_AGENT_SCREEN_CAPTURE=true
set RUC_AGENT_CAPTURE_MAX_WIDTH=800
set RUC_AGENT_CAPTURE_INTERVAL_MS=700

cd /d "%~dp0..\backend"
set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if not exist %MVN% set MVN=mvn

echo Backend: %RUC_BACKEND_BASE_URL%
echo Remote ID: %RUC_AGENT_REMOTE_ID%
%MVN% -q -DskipTests compile exec:java "-Dexec.mainClass=ru.ruc.desktop.engine.AgentSignalingClient"
pause
