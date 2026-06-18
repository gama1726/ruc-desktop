package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Captures the local screen and publishes JPEG frames to the media WebSocket (MVP media channel).
 */
final class AgentScreenCapture implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final java.net.http.WebSocket mediaSocket;
    private final AtomicBoolean running;
    private static final int MAX_JSON_CHARS = 600_000;

    private final int maxWidth;
    private final int intervalMs;
    private final float jpegQuality;

    AgentScreenCapture(
            java.net.http.WebSocket mediaSocket,
            AtomicBoolean running,
            int maxWidth,
            int intervalMs,
            float jpegQuality) {
        this.mediaSocket = mediaSocket;
        this.running = running;
        this.maxWidth = maxWidth;
        this.intervalMs = intervalMs;
        this.jpegQuality = jpegQuality;
    }

    @Override
    public void run() {
        try {
            Robot robot = new Robot();
            Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            while (running.get() && !mediaSocket.isOutputClosed()) {
                long started = System.currentTimeMillis();
                BufferedImage capture = robot.createScreenCapture(bounds);
                BufferedImage scaled = scaleDown(capture, maxWidth);
                byte[] jpeg = toJpeg(scaled, jpegQuality);
                sendFrame(scaled.getWidth(), scaled.getHeight(), jpeg);
                long elapsed = System.currentTimeMillis() - started;
                long sleep = Math.max(50L, intervalMs - elapsed);
                Thread.sleep(sleep);
            }
        } catch (Exception e) {
            System.out.println("[agent] screen capture stopped: " + e.getMessage());
        }
    }

    private static BufferedImage scaleDown(BufferedImage src, int maxW) {
        if (src.getWidth() <= maxW) {
            return src;
        }
        int h = (int) Math.round((double) src.getHeight() * maxW / src.getWidth());
        BufferedImage dst = new BufferedImage(maxW, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, maxW, h, null);
        g.dispose();
        return dst;
    }

    private static byte[] toJpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private void sendFrame(int width, int height, byte[] jpeg) {
        try {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("mime", "image/jpeg");
            payload.put("width", width);
            payload.put("height", height);
            payload.put("data", Base64.getEncoder().encodeToString(jpeg));

            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "frame");
            root.put("ts", System.currentTimeMillis());
            root.set("payload", payload);

            String json = MAPPER.writeValueAsString(root);
            if (json.length() > MAX_JSON_CHARS) {
                System.out.println(
                        "[agent] frame skipped (too large: "
                                + json.length()
                                + " chars, lower RUC_AGENT_CAPTURE_MAX_WIDTH)");
                return;
            }
            mediaSocket.sendText(json, true);
        } catch (Exception e) {
            System.out.println("[agent] frame send failed: " + e.getMessage());
        }
    }
}
