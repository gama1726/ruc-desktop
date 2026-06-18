package ru.ruc.desktop.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON-line bridge to the optional native WebRTC helper process. */
final class NativeHelperBridge {

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private volatile WebSocket attachedSocket;

    private NativeHelperBridge(Process process, BufferedWriter writer, BufferedReader reader) {
        this.process = process;
        this.writer = writer;
        this.reader = reader;
    }

    static NativeHelperBridge tryStart(AgentConfig cfg) {
        if (cfg.helperPath() == null || cfg.helperPath().isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(cfg.helperPath());
            if (!Files.exists(path)) {
                System.out.println("[agent] helper not found: " + cfg.helperPath());
                return null;
            }
            Process process = new ProcessBuilder(cfg.helperPath()).start();
            BufferedWriter writer =
                    new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            NativeHelperBridge bridge = new NativeHelperBridge(process, writer, reader);
            bridge.drainStartupLogs();
            bridge.startReaderThread();
            return bridge;
        } catch (Exception e) {
            System.out.println("[agent] helper start failed: " + e.getMessage());
            return null;
        }
    }

    void attachSocket(WebSocket webSocket) {
        this.attachedSocket = webSocket;
    }

    void sendToHelper(String type, String payloadJson) {
        try {
            writer.write("{\"type\":\"" + type + "\",\"payload\":" + payloadJson + "}");
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            System.out.println("[agent] helper write failed: " + e.getMessage());
        }
    }

    void drainToSocket(WebSocket webSocket) {
        attachSocket(webSocket);
        try {
            while (reader.ready()) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    break;
                }
                webSocket.sendText(line, true);
            }
        } catch (Exception e) {
            System.out.println("[agent] helper read failed: " + e.getMessage());
        }
    }

    private void startReaderThread() {
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.isBlank()) {
                                        continue;
                                    }
                                    WebSocket socket = attachedSocket;
                                    if (socket != null) {
                                        socket.sendText(line, true);
                                    }
                                }
                            } catch (Exception e) {
                                System.out.println("[agent] helper reader stopped: " + e.getMessage());
                            }
                        },
                        "ruc-native-helper-reader");
        thread.setDaemon(true);
        thread.start();
    }

    private void drainStartupLogs() {
        try {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    System.out.println("[agent] helper: " + line);
                }
            }
        } catch (Exception e) {
            System.out.println("[agent] helper startup read failed: " + e.getMessage());
        }
    }

    void shutdown() {
        attachedSocket = null;
        try {
            writer.write("{\"type\":\"shutdown\"}");
            writer.newLine();
            writer.flush();
        } catch (Exception ignored) {
            // ignore
        }
        process.destroy();
    }
}
