package ru.ruc.desktop.engine;

/** Runtime configuration for the host-side agent process. */
public record AgentConfig(
        String backendBaseUrl,
        String agentUid,
        String displayName,
        Long machineId,
        String remoteId,
        String ipAddress,
        long heartbeatIntervalSeconds,
        String helperPath,
        boolean webrtcJavaEnabled,
        int webrtcMaxFps,
        boolean screenCaptureEnabled,
        int captureMaxWidth,
        int captureIntervalMs,
        float captureJpegQuality) {

    public static AgentConfig fromEnv() {
        String base = getenv("RUC_BACKEND_BASE_URL", "http://localhost:8080");
        String uid = getenv("RUC_AGENT_UID", "agent-local-1");
        String displayName = getenv("RUC_AGENT_DISPLAY_NAME", "RUC Agent Local");
        String remoteId = getenv("RUC_AGENT_REMOTE_ID", "260227322");
        String ip = getenv("RUC_AGENT_IP", "127.0.0.1");
        String helperPath = getenv("RUC_AGENT_HELPER_PATH", "");
        boolean webrtcJava = !"false".equalsIgnoreCase(getenv("RUC_AGENT_WEBRTC", "true"));
        int webrtcFps = (int) parseLongOrDefault(System.getenv("RUC_AGENT_WEBRTC_MAX_FPS"), 15L);
        Long machineId = parseLongOrNull(System.getenv("RUC_AGENT_MACHINE_ID"));
        long heartbeat = parseLongOrDefault(System.getenv("RUC_AGENT_HEARTBEAT_SECONDS"), 15L);
        boolean screenCapture = !"false".equalsIgnoreCase(getenv("RUC_AGENT_SCREEN_CAPTURE", "true"));
        int maxWidth = (int) parseLongOrDefault(System.getenv("RUC_AGENT_CAPTURE_MAX_WIDTH"), 800L);
        int intervalMs = (int) parseLongOrDefault(System.getenv("RUC_AGENT_CAPTURE_INTERVAL_MS"), 700L);
        float jpegQuality = parseFloatOrDefault(System.getenv("RUC_AGENT_CAPTURE_JPEG_QUALITY"), 0.45f);
        return new AgentConfig(
                base,
                uid,
                displayName,
                machineId,
                remoteId,
                ip,
                heartbeat,
                helperPath.isBlank() ? null : helperPath,
                webrtcJava,
                webrtcFps,
                screenCapture,
                maxWidth,
                intervalMs,
                jpegQuality);
    }

    private static float parseFloatOrDefault(String value, float def) {
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String getenv(String key, String def) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? def : value;
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLongOrDefault(String value, long def) {
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
