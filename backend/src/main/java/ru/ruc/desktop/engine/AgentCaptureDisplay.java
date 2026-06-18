package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.onvoid.webrtc.media.video.VideoDesktopSource;
import dev.onvoid.webrtc.media.video.desktop.DesktopSource;
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps WebRTC desktop capture sources to AWT monitor bounds for video and mouse input. */
final class AgentCaptureDisplay {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ScreenEntry> screens = new ArrayList<>();
    private volatile ScreenEntry active;

    AgentCaptureDisplay() {
        reloadScreens();
    }

    void reloadScreens() {
        screens.clear();
        ScreenCapturer capturer = new ScreenCapturer();
        List<DesktopSource> sources = capturer.getDesktopSources();
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        capturer.dispose();

        int count = Math.min(sources.size(), devices.length);
        if (count == 0 && devices.length > 0) {
            for (int i = 0; i < devices.length; i++) {
                screens.add(fromDevice(i, "Монитор " + (i + 1), devices[i]));
            }
        } else {
            for (int i = 0; i < count; i++) {
                DesktopSource source = sources.get(i);
                String title =
                        source.title == null || source.title.isBlank()
                                ? "Монитор " + (i + 1)
                                : source.title;
                screens.add(new ScreenEntry((int) source.id, title, devices[i].getDefaultConfiguration().getBounds()));
            }
        }

        if (active == null || screens.stream().noneMatch(s -> s.desktopId == active.desktopId)) {
            active = screens.isEmpty() ? null : screens.get(0);
        }
        logActive();
    }

    List<ScreenEntry> screens() {
        return List.copyOf(screens);
    }

    ScreenEntry active() {
        return active;
    }

    boolean applyToCapture(VideoDesktopSource videoSource) {
        ScreenEntry screen = active;
        if (screen == null) {
            videoSource.setSourceId(0, false);
            return false;
        }
        videoSource.setSourceId(screen.desktopId, false);
        return true;
    }

    boolean selectByDesktopId(int desktopId) {
        Optional<ScreenEntry> found = screens.stream().filter(s -> s.desktopId == desktopId).findFirst();
        if (found.isEmpty()) {
            return false;
        }
        active = found.get();
        logActive();
        return true;
    }

    Point mapNormalized(double x, double y) {
        ScreenEntry screen = active;
        if (screen == null || x < 0 || x > 1 || y < 0 || y > 1) {
            return null;
        }
        Rectangle bounds = screen.bounds;
        int absX = bounds.x + (int) Math.round(x * Math.max(1, bounds.width - 1));
        int absY = bounds.y + (int) Math.round(y * Math.max(1, bounds.height - 1));
        return new Point(absX, absY);
    }

    String screensJson() {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "screens");
            ArrayNode arr = root.putArray("screens");
            for (ScreenEntry screen : screens) {
                ObjectNode item = arr.addObject();
                item.put("id", screen.desktopId);
                item.put("title", screen.title);
                item.put("width", screen.bounds.width);
                item.put("height", screen.bounds.height);
            }
            root.put("activeId", active == null ? 0 : active.desktopId);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"type\":\"screens\",\"screens\":[],\"activeId\":0}";
        }
    }

    private static ScreenEntry fromDevice(int desktopId, String title, GraphicsDevice device) {
        return new ScreenEntry(desktopId, title, device.getDefaultConfiguration().getBounds());
    }

    private void logActive() {
        if (active == null) {
            System.out.println("[agent] capture display: none");
            return;
        }
        Rectangle b = active.bounds;
        System.out.println(
                "[agent] capture display: "
                        + active.title
                        + " (id="
                        + active.desktopId
                        + ", "
                        + b.width
                        + "x"
                        + b.height
                        + " @"
                        + b.x
                        + ","
                        + b.y
                        + ")");
    }

    record ScreenEntry(int desktopId, String title, Rectangle bounds) {}

    record Point(int x, int y) {}
}
