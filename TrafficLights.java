import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Traffic Lights device. Draws the 12 lights (one per lane) and changes their
 * colors when the Controller sends SET_TRAFFIC_LIGHT. Which lanes are green
 * together is decided by the Controller, not here.
 */
public class TrafficLights {

    private static final Color GREEN  = Color.web("#3f9e2e");
    private static final Color YELLOW = Color.web("#f4e017");
    private static final Color RED    = Color.web("#cf1d1d");

    // keys look like "NORTH_LEFT" (see Multiplexor.laneKey)
    private final Map<String, Signal> signals = new HashMap<>();

    // used to blink the red lights when there is no power
    private Timeline blinkTimer;
    private boolean blinkOn = true;

    /**
     * Makes all 12 lights. onManualClick is called with the light's name
     * when the user clicks on it.
     *
     * Note: LEFT/RIGHT in the name is the way the car turns. The "LEFT",
     * "UP", etc. passed to arrow() is just which way the arrow points on
     * the screen, so they don't always match.
     */
    public TrafficLights(Pane root, Consumer<String> onManualClick) {
        // NORTH approach (cars going down the screen)
        register("NORTH_RIGHT",    arrow(root, 325, 270, "LEFT"),  onManualClick);
        register("NORTH_STRAIGHT", circle(root, 389, 270),         onManualClick);
        register("NORTH_LEFT",     arrow(root, 453, 270, "RIGHT"), onManualClick);

        // SOUTH approach (cars going up)
        register("SOUTH_LEFT",     arrow(root, 575, 690, "LEFT"),  onManualClick);
        register("SOUTH_STRAIGHT", circle(root, 639, 690),         onManualClick);
        register("SOUTH_RIGHT",    arrow(root, 703, 690, "RIGHT"), onManualClick);

        // WEST approach (cars going right)
        register("WEST_LEFT",      arrow(root, 300, 535, "UP"),    onManualClick);
        register("WEST_STRAIGHT",  circle(root, 300, 595),         onManualClick);
        register("WEST_RIGHT",     arrow(root, 300, 655, "DOWN"),  onManualClick);

        // EAST approach (cars going left)
        register("EAST_RIGHT",     arrow(root, 728, 300, "UP"),    onManualClick);
        register("EAST_STRAIGHT",  circle(root, 728, 360),         onManualClick);
        register("EAST_LEFT",      arrow(root, 728, 420, "DOWN"),  onManualClick);
    }

    private void register(String name, Signal signal, Consumer<String> onManualClick) {
        signals.put(name, signal);
        signal.shape.setCursor(Cursor.HAND);
        signal.shape.setOnMouseClicked(e -> onManualClick.accept(name));
    }

    /** Arrow shaped light for a turn lane. */
    private Signal arrow(Pane root, double cx, double cy, String dir) {
        Polygon a = new Polygon(
                  0, -29,
                 22,  -3,
                 10,  -3,
                 10,  29,
                -10,  29,
                -10,  -3,
                -22,  -3);
        a.setLayoutX(cx);
        a.setLayoutY(cy);
        a.setRotate(switch (dir) {
            case "RIGHT" -> 90;
            case "DOWN"  -> 180;
            case "LEFT"  -> 270;
            default      -> 0;
        });
        root.getChildren().add(a);
        return new Signal(a);
    }

    /** Round light for a straight lane. */
    private Signal circle(Pane root, double cx, double cy) {
        Circle c = new Circle(cx, cy, 25);
        root.getChildren().add(c);
        return new Signal(c);
    }

    /** Sets every light to red. */
    public void allRed() {
        for (Signal s : signals.values()) s.setLight(Light.RED);
    }

    /** Power failure: every light turns red and blinks on and off every half second. */
    public void startBlinkingRed() {
        if (blinkTimer != null) return; // already blinking
        allRed();
        blinkTimer = new Timeline(new KeyFrame(Duration.seconds(0.5), e -> {
            blinkOn = !blinkOn;
            for (Signal s : signals.values()) {
                s.shape.setOpacity(blinkOn ? 1.0 : 0.15);
            }
        }));
        blinkTimer.setCycleCount(Animation.INDEFINITE);
        blinkTimer.play();
    }

    /** Power is back: stop blinking. */
    public void stopBlinking() {
        if (blinkTimer != null) {
            blinkTimer.stop();
            blinkTimer = null;
        }
        blinkOn = true;
        allRed();
    }

    /** Handles SET_TRAFFIC_LIGHT. Returns false if there is no such lane. */
    public boolean setOutput(Multiplexor.Direction direction, Multiplexor.Lane lane,
                             Multiplexor.Display display, Multiplexor.SignalColor color) {
        Signal signal = signals.get(Multiplexor.laneKey(direction, lane));
        if (signal == null) return false;
        signal.setOutput(display, color);
        return true;
    }

    /** Clicking a light changes it to the next color (just for the demo). */
    public boolean cycle(String name) {
        Signal signal = signals.get(name);
        if (signal == null) return false;
        signal.setLight(signal.light.next());
        return true;
    }

    /** Cars use this to know if they can go. */
    public boolean isGreen(String name) {
        Signal signal = signals.get(name);
        return signal != null && signal.light == Light.GREEN;
    }

    /** Keeps the lights on top so cars don't cover them. */
    public void bringAllToFront() {
        for (Signal s : signals.values()) s.shape.toFront();
    }

    private enum Light {
        GREEN, YELLOW, RED;

        /** GREEN -> YELLOW -> RED -> GREEN */
        Light next() {
            return values()[(ordinal() + 1) % values().length];
        }

        Color color() {
            return switch (this) {
                case GREEN  -> TrafficLights.GREEN;
                case YELLOW -> TrafficLights.YELLOW;
                case RED    -> TrafficLights.RED;
            };
        }
    }

    /** One light: its shape on the screen and its current color. */
    private static final class Signal {
        private final Shape shape;
        private Light light = Light.RED;

        Signal(Shape shape) {
            this.shape = shape;
            shape.setStroke(Color.web("#00000055"));
            shape.setStrokeWidth(1.5);
            setLight(Light.RED);
        }

        void setLight(Light newLight) {
            light = newLight;
            shape.setOpacity(1.0);
            shape.setFill(light.color());
        }

        void setOutput(Multiplexor.Display display, Multiplexor.SignalColor color) {
            if (display == Multiplexor.Display.OFF) {
                shape.setOpacity(0.15); // looks turned off
                return;
            }
            setLight(Light.valueOf(color.name()));
        }
    }
}
