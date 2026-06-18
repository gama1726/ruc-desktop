package ru.ruc.desktop.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/** Applies remote mouse/keyboard events from the WebRTC data channel. */
final class AgentRemoteInput {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Robot robot;
    private final Dimension screenSize;

    AgentRemoteInput() throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(0);
        this.screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    }

    static AgentRemoteInput tryCreate() {
        try {
            return new AgentRemoteInput();
        } catch (AWTException e) {
            System.out.println("[agent] remote input unavailable: " + e.getMessage());
            return null;
        }
    }

    void handleMessage(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String type = node.path("type").asText("");
            switch (type) {
                case "mousemove" -> mouseMove(node);
                case "mousedown" -> mousePress(node, true);
                case "mouseup" -> mousePress(node, false);
                case "wheel" -> mouseWheel(node);
                case "keydown" -> keyPress(node, true);
                case "keyup" -> keyPress(node, false);
                default -> {
                    // ignore unknown input events
                }
            }
        } catch (Exception e) {
            System.out.println("[agent] remote input parse error: " + e.getMessage());
        }
    }

    private void mouseMove(JsonNode node) {
        Point point = normalizedPoint(node);
        if (point == null) {
            return;
        }
        robot.mouseMove(point.x, point.y);
    }

    private void mousePress(JsonNode node, boolean down) {
        Point point = normalizedPoint(node);
        if (point != null) {
            robot.mouseMove(point.x, point.y);
        }
        int mask = mouseButtonMask(node.path("button").asInt(0));
        if (down) {
            robot.mousePress(mask);
        } else {
            robot.mouseRelease(mask);
        }
    }

    private void mouseWheel(JsonNode node) {
        Point point = normalizedPoint(node);
        if (point != null) {
            robot.mouseMove(point.x, point.y);
        }
        int deltaY = node.path("deltaY").asInt(0);
        if (deltaY == 0) {
            return;
        }
        int clicks = (int) Math.signum(deltaY) * Math.max(1, Math.abs(deltaY) / 40);
        robot.mouseWheel(-clicks);
    }

    private void keyPress(JsonNode node, boolean down) {
        int keyCode = mapKeyCode(node.path("code").asText(""), node.path("key").asText(""));
        if (keyCode == KeyEvent.VK_UNDEFINED) {
            return;
        }
        if (down) {
            robot.keyPress(keyCode);
        } else {
            robot.keyRelease(keyCode);
        }
    }

    private Point normalizedPoint(JsonNode node) {
        if (!node.has("x") || !node.has("y")) {
            return null;
        }
        double x = node.path("x").asDouble(Double.NaN);
        double y = node.path("y").asDouble(Double.NaN);
        if (Double.isNaN(x) || Double.isNaN(y) || x < 0 || x > 1 || y < 0 || y > 1) {
            return null;
        }
        int absX = (int) Math.round(x * (screenSize.width - 1));
        int absY = (int) Math.round(y * (screenSize.height - 1));
        return new Point(absX, absY);
    }

    private static int mouseButtonMask(int button) {
        return switch (button) {
            case 1 -> InputEvent.BUTTON2_DOWN_MASK;
            case 2 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }

    private static int mapKeyCode(String code, String key) {
        return switch (code) {
            case "Enter" -> KeyEvent.VK_ENTER;
            case "Backspace" -> KeyEvent.VK_BACK_SPACE;
            case "Tab" -> KeyEvent.VK_TAB;
            case "Escape" -> KeyEvent.VK_ESCAPE;
            case "Space" -> KeyEvent.VK_SPACE;
            case "ArrowLeft" -> KeyEvent.VK_LEFT;
            case "ArrowRight" -> KeyEvent.VK_RIGHT;
            case "ArrowUp" -> KeyEvent.VK_UP;
            case "ArrowDown" -> KeyEvent.VK_DOWN;
            case "Delete" -> KeyEvent.VK_DELETE;
            case "Home" -> KeyEvent.VK_HOME;
            case "End" -> KeyEvent.VK_END;
            case "PageUp" -> KeyEvent.VK_PAGE_UP;
            case "PageDown" -> KeyEvent.VK_PAGE_DOWN;
            case "ControlLeft", "ControlRight" -> KeyEvent.VK_CONTROL;
            case "ShiftLeft", "ShiftRight" -> KeyEvent.VK_SHIFT;
            case "AltLeft", "AltRight" -> KeyEvent.VK_ALT;
            case "MetaLeft", "MetaRight" -> KeyEvent.VK_WINDOWS;
            default -> {
                if (code.startsWith("Key") && code.length() == 4) {
                    yield KeyEvent.getExtendedKeyCodeForChar(code.charAt(3));
                }
                if (code.startsWith("Digit") && code.length() == 6) {
                    yield KeyEvent.getExtendedKeyCodeForChar(code.charAt(5));
                }
                if (key != null && key.length() == 1) {
                    yield KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
                }
                yield KeyEvent.VK_UNDEFINED;
            }
        };
    }

    private record Point(int x, int y) {}
}
