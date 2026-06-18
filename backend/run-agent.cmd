@echo off

cd /d "%~dp0"


set RUC_AGENT_WEBRTC=true

set RUC_AGENT_WEBRTC_MAX_FPS=60

set RUC_AGENT_CAPTURE_MAX_WIDTH=1920

set RUC_AGENT_SCREEN_CAPTURE=false

set RUC_BACKEND_BASE_URL=http://10.10.31.194

set RUC_AGENT_REMOTE_ID=260227322

set RUC_AGENT_WEBRTC=true

set RUC_AGENT_WEBRTC_MAX_FPS=15

set RUC_AGENT_SCREEN_CAPTURE=false

set RUC_AGENT_CAPTURE_MAX_WIDTH=1280

set RUC_AGENT_CAPTURE_INTERVAL_MS=700

set RUC_AGENT_CAPTURE_JPEG_QUALITY=0.45



set MVN="C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"

if not exist %MVN% (

  echo Maven not found. Install Maven or fix MVN path in run-agent.cmd

  exit /b 1

)



echo Starting RUC Agent (WebRTC desktop capture)...

echo Backend: %RUC_BACKEND_BASE_URL%

echo Remote ID: %RUC_AGENT_REMOTE_ID%

echo WebRTC: %RUC_AGENT_WEBRTC%, JPEG media: %RUC_AGENT_SCREEN_CAPTURE%

echo.



%MVN% -DskipTests compile exec:java "-Dexec.mainClass=ru.ruc.desktop.engine.AgentSignalingClient"

pause

