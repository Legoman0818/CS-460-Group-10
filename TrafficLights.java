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
 * TrafficLights - the 12 traffic lights, one per lane. (Walkthrough 3.6)
 *
 * Design Diagram: the Traffic Lights box.
 *
 * API: SET_TRAFFIC_LIGHT calls setOutput().
 *
 * This class just shows whatever color it's told. Which lanes are green
 * together is decided by the Controller, not here.
 */
public class TrafficLights {

    private static final Color GREEN  = Color.web("#3f9e2e");
    private static final Color YELLOW = Color.web("#f4e017");
    private static final Color RED    = Color.web("#cf1d1d");

    // all 12 lights by name, the names look like "NORTH_LEFT" (see Multiplexor.laneKey)
    private final Map<String, Signal> signals = new HashMap<>();

    // for blinking the red lights when there's no power
    private Timeline blinkTimer;
    private boolean blinkOn = true;

    /**
     * Draws all 12 lights. Turn lanes get arrow shapes and straight lanes get
     * circles. onManualClick gets called with the light's name when someone
     * clicks it (for the manual demo).
     *
     * Heads up: LEFT/RIGHT in the name is the way the CAR turns. The "LEFT",
     * "UP", etc. passed to arrow() is just which way the arrow points on the
     * screen, so they don't always match.
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

    /** Saves the light in the map and makes it clickable. */
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
        for (Signal s : signals.values()) s.setColor(Multiplexor.SignalColor.RED);
    }

    /** Power failure (called by PowerSensor): every light goes red and blinks every half second. */
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

    /** Power is back (called by PowerSensor): stop blinking, stay solid red. */
    public void stopBlinking() {
        if (blinkTimer != null) {
            blinkTimer.stop();
            blinkTimer = null;
        }
        blinkOn = true;
        allRed();
    }

    /** SET_TRAFFIC_LIGHT ends up here. Finds the light and sets it. False if there's no such lane. */
    public boolean setOutput(Multiplexor.Direction direction, Multiplexor.Lane lane,
                             Multiplexor.Display display, Multiplexor.SignalColor color) {
        Signal signal = signals.get(Multiplexor.laneKey(direction, lane));
        if (signal == null) return false;
        signal.setOutput(display, color);
        return true;
    }

    /** The manual click (just for the demo): changes the light to the next color. */
    public boolean cycle(String name) {
        Signal signal = signals.get(name);
        if (signal == null) return false;
        // GREEN -> YELLOW -> RED -> GREEN
        switch (signal.color) {
            case GREEN  -> signal.setColor(Multiplexor.SignalColor.YELLOW);
            case YELLOW -> signal.setColor(Multiplexor.SignalColor.RED);
            case RED    -> signal.setColor(Multiplexor.SignalColor.GREEN);
        }
        return true;
    }

    /** The cars call this every frame to know if they can go. */
    public boolean isGreen(String name) {
        Signal signal = signals.get(name);
        return signal != null && signal.color == Multiplexor.SignalColor.GREEN;
    }

    /** Keeps the lights on top so the cars don't cover them up. */
    public void bringAllToFront() {
        for (Signal s : signals.values()) s.shape.toFront();
    }

    /** One light: its shape on the screen and the color it's showing. */
    private static final class Signal {
        private final Shape shape;
        private Multiplexor.SignalColor color = Multiplexor.SignalColor.RED;

        Signal(Shape shape) {
            this.shape = shape;
            shape.setStroke(Color.web("#00000055"));
            shape.setStrokeWidth(1.5);
            setColor(Multiplexor.SignalColor.RED);
        }

        void setColor(Multiplexor.SignalColor newColor) {
            color = newColor;
            shape.setOpacity(1.0);
            if (color == Multiplexor.SignalColor.GREEN) shape.setFill(GREEN);
            else if (color == Multiplexor.SignalColor.YELLOW) shape.setFill(YELLOW);
            else shape.setFill(RED);
        }

        void setOutput(Multiplexor.Display display, Multiplexor.SignalColor color) {
            if (display == Multiplexor.Display.OFF) {
                shape.setOpacity(0.15); // dim it so it looks turned off
                return;
            }
            setColor(color);
        }
    }
}
