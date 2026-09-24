import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;

/**
 * Traffic Lights device.
 *
 * Owns the twelve programmable LED signals (one arrow or circle per approach
 * lane) drawn on the intersection. Cross Walk forwards every
 * SET_TRAFFIC_LIGHT, CYCLE_SIGNAL, and failsafe request to this class instead
 * of reaching into JavaFX shapes itself, matching the standalone "Traffic
 * Lights" box in the design diagram.
 */
public class TrafficLights {

    private static final Color GREEN  = Color.web("#3f9e2e");
    private static final Color YELLOW = Color.web("#f4e017");
    private static final Color RED    = Color.web("#cf1d1d");

    private final List<Signal> signals = new ArrayList<>();
    private final Map<String, Signal> signalByName = new HashMap<>();

    /**
     * Builds all twelve signals and reports manual clicks through onManualClick.
     *
     * Each register(name, ...) call's name is the real-world turn the lane at
     * that (fixed) screen position performs, using the standard driving
     * convention: LEFT is the lane next to the center line (crosses opposing
     * through traffic), RIGHT is the lane next to the curb. The arrow glyph's
     * own rotation (the "LEFT"/"RIGHT"/"UP"/"DOWN" argument to arrow(...)) is
     * a separate, unrelated concept -- it is just the compass direction that
     * arrow visually points on screen, fixed by the lane's real geometry, and
     * does not always match the name it is registered under.
     */
    public TrafficLights(Pane root, Consumer<String> onManualClick) {
        // NORTH approach (traveling south): the curb lane (screen-west side,
        // x=325) turns toward WEST -- a real RIGHT turn. The center-line lane
        // (x=453) turns toward EAST -- a real LEFT turn.
        register("NORTH_RIGHT",    arrow(root, 325, 270, "LEFT",  Light.YELLOW), onManualClick);
        register("NORTH_STRAIGHT", circle(root, 389, 270, Light.GREEN),          onManualClick);
        register("NORTH_LEFT",     arrow(root, 453, 270, "RIGHT", Light.RED),    onManualClick);

        // SOUTH approach (traveling north): already matches the convention.
        register("SOUTH_LEFT",     arrow(root, 575, 690, "LEFT",  Light.RED),    onManualClick);
        register("SOUTH_STRAIGHT", circle(root, 639, 690, Light.GREEN),          onManualClick);
        register("SOUTH_RIGHT",    arrow(root, 703, 690, "RIGHT", Light.YELLOW), onManualClick);

        // WEST approach (traveling east): already matches the convention.
        register("WEST_LEFT",      arrow(root, 300, 535, "UP",    Light.RED),    onManualClick);
        register("WEST_STRAIGHT",  circle(root, 300, 595, Light.RED),            onManualClick);
        register("WEST_RIGHT",     arrow(root, 300, 655, "DOWN",  Light.YELLOW), onManualClick);

        // EAST approach (traveling west): the curb lane (screen-north side,
        // y=300) turns toward NORTH -- a real RIGHT turn. The center-line
        // lane (y=420) turns toward SOUTH -- a real LEFT turn.
        register("EAST_RIGHT",     arrow(root, 728, 300, "UP",    Light.YELLOW), onManualClick);
        register("EAST_STRAIGHT",  circle(root, 728, 360, Light.RED),            onManualClick);
        register("EAST_LEFT",      arrow(root, 728, 420, "DOWN",  Light.RED),    onManualClick);
    }

    /** Stores a signal by name and makes its JavaFX shape clickable. */
    private void register(String name, Signal signal, Consumer<String> onManualClick) {
        signalByName.put(name, signal);
        signals.add(signal);
        signal.shape.setCursor(Cursor.HAND);
        signal.shape.setOnMouseClicked(e -> onManualClick.accept(name));
    }

    /** Builds an arrow-shaped programmable LED signal. */
    private Signal arrow(Pane root, double cx, double cy, String dir, Light initial) {
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
        return new Signal(a, initial);
    }

    /** Builds a round programmable LED signal for a straight lane. */
    private Signal circle(Pane root, double cx, double cy, Light initial) {
        Circle c = new Circle(cx, cy, 25);
        root.getChildren().add(c);
        return new Signal(c, initial);
    }

    /** Forces every signal into the failsafe red state. */
    public void allRed() {
        for (Signal s : signals) s.forceRed();
    }

    /** Turns every lane in this pattern green; every other lane goes red first. */
    public void showGreen(Pattern pattern) {
        allRed();
        for (String lane : pattern.lanes()) setLight(lane, Light.GREEN);
    }

    /** Warns of an upcoming pattern change without touching the other lanes. */
    public void showYellow(Pattern pattern) {
        for (String lane : pattern.lanes()) setLight(lane, Light.YELLOW);
    }

    /** Turns one lane green directly, e.g. to escort an emergency vehicle. */
    public void setLaneGreen(Multiplexor.Direction direction, Multiplexor.Lane lane) {
        setLight(Multiplexor.laneKey(direction, lane), Light.GREEN);
    }

    private void setLight(String name, Light light) {
        Signal s = signalByName.get(name);
        if (s != null) s.setLight(light);
    }

    /** Applies a SET_TRAFFIC_LIGHT command. Returns false if the lane is unknown. */
    public boolean setOutput(Multiplexor.Direction direction, Multiplexor.Lane lane,
                             Multiplexor.Display display, Multiplexor.SignalColor color) {
        Signal signal = signalByName.get(Multiplexor.laneKey(direction, lane));
        if (signal == null) return false;
        signal.setOutput(display, color);
        return true;
    }

    /** Cycles one named signal when a user clicks it (simulation only). */
    public boolean cycle(String name) {
        Signal signal = signalByName.get(name);
        if (signal == null) return false;
        signal.nextLight();
        return true;
    }

    /** Reports whether the named lane's signal is currently green. */
    public boolean isGreen(String name) {
        Signal signal = signalByName.get(name);
        return signal != null && signal.isGreen();
    }

    /** Keeps every signal visible above cars passing behind it. */
    public void bringAllToFront() {
        for (Signal s : signals) s.bringToFront();
    }

    /**
     * A basic four-phase real-intersection cycle: through traffic (with its
     * same-direction right turns) gets one phase, then a protected left-turn
     * phase, for each street in turn. Self-explanatory names double as the
     * lane keys (Multiplexor.laneKey format) that belong to that phase.
     */
    public enum Pattern {
        NORTH_SOUTH_LEFT("NORTH_LEFT", "SOUTH_LEFT"),
        NORTH_SOUTH_THROUGH("NORTH_STRAIGHT", "NORTH_RIGHT", "SOUTH_STRAIGHT", "SOUTH_RIGHT"),
        EAST_WEST_LEFT("EAST_LEFT", "WEST_LEFT"),
        EAST_WEST_THROUGH("EAST_STRAIGHT", "EAST_RIGHT", "WEST_STRAIGHT", "WEST_RIGHT");
        

        private final String[] lanes;

        Pattern(String... lanes) {
            this.lanes = lanes;
        }

        String[] lanes() {
            return lanes;
        }
    }

    // Small signal state machine used when a user clicks an individual light.
    private enum Light {
        GREEN, YELLOW, RED;

        Light next() {
            // Enum order makes the cycle GREEN -> YELLOW -> RED -> GREEN.
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

    // Stores and paints the current state of one programmable signal.
    private static final class Signal {
        private Light light;
        private final Shape shape;

        Signal(Shape shape, Light initial) {
            this.shape = shape;
            this.light = initial;
            this.shape.setStroke(Color.web("#00000055"));
            this.shape.setStrokeWidth(1.5);
            paint();
        }

        private void paint() {
            shape.setFill(light.color());
        }

        /** Failsafe operation used for crossings, emergencies, and power loss. */
        void forceRed() {
            light = Light.RED;
            shape.setOpacity(1.0);
            paint();
        }

        void setLight(Light newLight) {
            light = newLight;
            shape.setOpacity(1.0);
            paint();
        }

        void setOutput(Multiplexor.Display display, Multiplexor.SignalColor color) {
            if (display == Multiplexor.Display.OFF) {
                shape.setOpacity(0.15);
                return;
            }
            shape.setOpacity(1.0);
            setLight(Light.valueOf(color.name()));
        }

        void nextLight() {
            light = light.next();
            shape.setOpacity(1.0);
            paint();
        }

        boolean isGreen() {
            // Cars call this method before crossing their stop line.
            return light == Light.GREEN;
        }

        void bringToFront() {
            shape.toFront();
        }
    }
}
