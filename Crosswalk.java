import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 *   Launch from Main with a JavaFX-bundled JDK (e.g. Azul Zulu FX)
 */
public class Crosswalk extends Application {

    /*
     * PRESENTATION GUIDE
     * ------------------
     * start()              builds the JavaFX window and calls each drawing method.
     * signals()            creates the clickable traffic lights.
     * trafficSimulation()  creates the three cars and runs their animation.
     * Signal               stores a light's current GREEN/YELLOW/RED state.
     * Car.java             contains the actual stopping and turning logic.
     */

    // pane
    private static final double W = 1024;
    private static final double H = 945;

    // colors
    private static final Color BG      = Color.web("#0d0d0d");
    private static final Color PAINT   = Color.web("#f0f0f0"); // white road paint

    private static final Color GREEN   = Color.web("#3f9e2e");
    private static final Color YELLOW  = Color.web("#f4e017");
    private static final Color RED     = Color.web("#cf1d1d");
    private static final Color ORANGE  = Color.web("#ff8c1a"); // pedestrian alarm state

    private final Pane root = new Pane();

    // clickable state
    private final List<Signal> signals = new ArrayList<>();
    private final List<PedZone> pedZones = new ArrayList<>();
    private final Map<String, Signal> signalByName = new HashMap<>();
    private final Map<String, PedZone> pedestrianByName = new HashMap<>();
    private boolean pedAlarm = false;   // toggled by clicking any pedestrian marker
    private DigitalTwinServer socketServer;
    private Signal eastLeftSignal;
    private Signal eastStraightSignal;
    private Signal eastRightSignal;
    private Signal southLeftSignal;
    private Signal southStraightSignal;
    private Signal southRightSignal;
    private Signal northLeftSignal;
    private Signal northStraightSignal;
    private Signal northRightSignal;
    private Signal westLeftSignal;
    private Signal westStraightSignal;
    private Signal westRightSignal;

    @Override
    public void start(Stage stage) {
        root.setPrefSize(W, H);

        // Draw the intersection one layer at a time. Later items appear on top.
        background();
        stopLines();   
        roads();
        crosswalks();
        laneArrows();
        pedestrians();
        antenna();
        signals();

        // Listen for commands sent by Multiplexor through localhost port 5000.
        startSocketServer();

        // Cars are added last so they are visible above the road markings.
        trafficSimulation();

        
        Group content = new Group(root);
        Pane frame = new Pane(content);
        frame.setStyle("-fx-background-color: #0d0d0d;");
        content.scaleXProperty().bind(
                javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> Math.min(frame.getWidth() / W, frame.getHeight() / H),
                        frame.widthProperty(), frame.heightProperty()));
        content.scaleYProperty().bind(content.scaleXProperty());

        Scene scene = new Scene(frame, W, H, BG);
        stage.setTitle("Crosswalk - Traffic Control System (Group 10)");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // Release the socket when the JavaFX window closes.
        if (socketServer != null) socketServer.close();
    }

    // helpers
    private void add(Node... nodes) {
        root.getChildren().addAll(nodes);
    }

    private Line paint(double x1, double y1, double x2, double y2, double w) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(w);
        l.setStrokeLineCap(StrokeLineCap.BUTT);
        return l;
    }

    private Line dashed(double x1, double y1, double x2, double y2, double w, double on, double off) {
        Line l = paint(x1, y1, x2, y2, w);
        l.getStrokeDashArray().addAll(on, off);
        return l;
    }

    // layers
    private void background() {
        Rectangle bg = new Rectangle(0, 0, W, H);
        bg.setFill(BG);
        add(bg);
    }

    private void roads() {
        // intersection box corners
        double xL = 293, xR = 735, yT = 246, yB = 713;

        // vertical road edges (above and below the intersection)
        add(paint(xL, 0, xL, yT, 3));
        add(paint(xR, 0, xR, yT, 3));
        add(paint(xL, yB, xL, H, 3));
        add(paint(xR, yB, xR, H, 3));

        // horizontal road edges (left and right of the intersection)
        add(paint(0, yT, xL, yT, 3));
        add(paint(0, yB, xL, yB, 3));
        add(paint(xR, yT, W, yT, 3));
        add(paint(xR, yB, W, yB, 3));

        // center lines
        add(dashed(514, 0, 514, yT, 3, 22, 18));
        add(dashed(514, yB, 514, H, 3, 22, 18));
        add(dashed(0, 480, xL, 480, 3, 22, 18));
        add(dashed(xR, 480, W, 480, 3, 22, 18));

        // lanes(the boxes the turn arrows sit in). 3 lanes each
        // left 
        for (double y : new double[]{486, 559, 632, 705}) add(paint(0, y, 210, y, 2));
        add(paint(210, 486, 210, 705, 2));
        // right
        for (double y : new double[]{252, 324, 396, 468}) add(paint(812, y, W, y, 2));
        add(paint(812, 252, 812, 468, 2));
        // top 
        for (double x : new double[]{293, 357, 421, 485}) add(paint(x, 0, x, 168, 2));
        add(paint(293, 168, 485, 168, 2));
        // bottom 
        for (double x : new double[]{543, 607, 671, 735}) add(paint(x, 775, x, H, 2));
        add(paint(543, 775, 735, 775, 2));
    }

    private void crosswalks() {
        crosswalkTicks(318, 709, 200, 232, true);   // top
        crosswalkTicks(318, 709, 724, 756, true);   // bottom
        crosswalkTicks(246, 272, 292, 686, false);  // left
        crosswalkTicks(762, 788, 292, 686, false);  // right
    }

    // ladder-style crosswalk. vertical=true -> vertical ticks spread along x
    private void crosswalkTicks(double a1, double a2, double b1, double b2, boolean vertical) {
        int n = 26;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            if (vertical) {
                double x = a1 + t * (a2 - a1);
                add(paint(x, b1, x, b2, 3));
            } else {
                double y = b1 + t * (b2 - b1);
                add(paint(a1, y, a2, y, 3));
            }
        }
    }

    private void stopLines() {
        add(paint(293, 198, 514, 198, 6)); // north 
        add(paint(514, 758, 735, 758, 6)); // south 
        add(paint(244, 480, 244, 713, 6)); // west 
        add(paint(790, 246, 790, 480, 6)); // east
    }

    //  white lane arrows 
    // Each approach has 3 lanes: [turn left] [straight] [turn right],
    private void laneArrows() {
        // top. traffic travels down
        laneArrow(325, 12, 0, 1, -1, 0);
        laneArrow(389, 12, 0, 1, 0, 0);
        laneArrow(453, 12, 0, 1, 1, 0);
        // bottom. traffic travels up
        laneArrow(575, 933, 0, -1, -1, 0);
        laneArrow(639, 933, 0, -1, 0, 0);
        laneArrow(703, 933, 0, -1, 1, 0);
        // left. traffic travels right 
        laneArrow(6, 548, 1, 0, 0, -1, 74, 44, 150);
        laneArrow(6, 595, 1, 0, 0,  0, 74, 44, 150);
        laneArrow(6, 642, 1, 0, 0,  1, 74, 44, 150);
        // right. traffic travels left
        laneArrow(1018, 300, -1, 0, 0, -1, 74, 40, 150);
        laneArrow(1018, 360, -1, 0, 0,  0, 74, 40, 150);
        laneArrow(1018, 420, -1, 0, 0,  1, 74, 40, 150);
    }

    /**
     * arrows
     * @param ox,oy   tail of the arrow
     * @param fx,fy   travel direction
     * @param cx,cy   arrow curl (0,0 = go straight)
     */
    private void laneArrow(double ox, double oy, double fx, double fy, double cx, double cy) {
        laneArrow(ox, oy, fx, fy, cx, cy, 92, 24, 140);
    }

    private void laneArrow(double ox, double oy, double fx, double fy, double cx, double cy,
                           double run, double turn, double straightLen) {
        Path p = new Path();
        p.setStroke(PAINT);
        p.setFill(null);
        p.setStrokeWidth(5);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);

        if (cx == 0 && cy == 0) {
            double ex = ox + fx * straightLen, ey = oy + fy * straightLen;
            p.getElements().add(new MoveTo(ox, oy));
            p.getElements().add(new LineTo(ex, ey));
            head(p, ex, ey, fx, fy);
        } else {
            double l1 = run, l2 = turn;             // part before the bend, then the turn
            double kx = ox + fx * l1, ky = oy + fy * l1;   // knee of the bend
            double ex = kx + cx * l2, ey = ky + cy * l2;
            p.getElements().add(new MoveTo(ox, oy));
            p.getElements().add(new LineTo(ox + fx * (l1 - 18), oy + fy * (l1 - 18)));
            p.getElements().add(new QuadCurveTo(kx, ky, kx + cx * 18, ky + cy * 18));
            p.getElements().add(new LineTo(ex, ey));
            head(p, ex, ey, cx, cy);
        }
        add(p);
    }

    private void head(Path p, double tx, double ty, double dx, double dy) {
        double s = 13;
        double bx = tx - dx * s, by = ty - dy * s;
        double px = -dy, py = dx; // perpendicular
        p.getElements().add(new MoveTo(bx + px * s, by + py * s));
        p.getElements().add(new LineTo(tx, ty));
        p.getElements().add(new LineTo(bx - px * s, by - py * s));
    }

    // pedestrian crossing signs
    // One square per corner with a stick figure 
    // Clicking anywhere in a square makes every square + figure turn
    // orange and every colored signal turn red
    private void pedestrians() {
        pedestrianZone("NW", 250, 198);   // north-west corner
        pedestrianZone("NE", 783, 210);   // north-east corner
        pedestrianZone("SW", 250, 765);   // south-west corner
        pedestrianZone("SE", 785, 765);   // south-east corner
    }

    private void pedestrianZone(String name, double cx, double cy) {
        double half = 26;
        Rectangle box = new Rectangle(cx - half, cy - half, half * 2, half * 2);
        box.setFill(Color.TRANSPARENT);   // transparent, but clickable
        box.setStroke(PAINT);
        box.setStrokeWidth(2);
        box.setPickOnBounds(true);        

        Circle head = new Circle(cx, cy - 11, 6);
        head.setStroke(PAINT);
        head.setStrokeWidth(2);
        head.setFill(Color.TRANSPARENT);
        Line body = strokeLine(cx, cy - 5, cx, cy + 6);
        Line arms = strokeLine(cx - 8, cy - 1, cx + 8, cy - 1);
        Line legL = strokeLine(cx, cy + 6, cx - 7, cy + 17);
        Line legR = strokeLine(cx, cy + 6, cx + 7, cy + 17);

        Group g = new Group(box, head, body, arms, legL, legR);
        g.setCursor(Cursor.HAND);
        g.setOnMouseClicked(e -> {
            setPedestrianAlarm(!pedAlarm);
        });

        PedZone zone = new PedZone(box, head, body, arms, legL, legR);
        pedZones.add(zone);
        pedestrianByName.put(name, zone);
        add(g);
    }

    private void setPedestrianAlarm(boolean active) {
        pedAlarm = active;
        for (PedZone zone : pedZones) {
            if (active) zone.alarm();
            else zone.clear();
        }
        if (active) allSignalsRed();
    }

    private Line strokeLine(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(2);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    private void antenna() {
        add(strokeLine(823, 178, 823, 197));
        Text label = new Text(834, 201, "Antenna");
        label.setFill(PAINT);
        label.setFont(Font.font(12));

        // click antenna, all signals turn red
        Circle knob = new Circle(823, 172, 6);
        knob.setFill(PAINT);
        knob.setStroke(RED);
        knob.setStrokeWidth(1.5);
        knob.setCursor(Cursor.HAND);
        knob.setOnMouseClicked(e -> activateEmergencyMode());

        add(label, knob);
    }

    // colored, clickable signals 
    private void signals() {
        /*
         * These first three signals are physically beside the north entrance.
         * They control traffic coming from the opposite side, not our top cars.
         */
        // North group (vertical, near x = 335)
        eastLeftSignal = arrowSignal(335, 292, "UP", Light.YELLOW);
        eastStraightSignal = circleSignal(335, 366, Light.RED);
        eastRightSignal = arrowSignal(335, 440, "DOWN", Light.RED);

        // East group (horizontal, near y = 291)
        southLeftSignal = arrowSignal(556, 291, "LEFT", Light.RED);
        southStraightSignal = circleSignal(625, 291, Light.GREEN);
        southRightSignal = arrowSignal(694, 291, "RIGHT", Light.YELLOW);

        /*
         * IMPORTANT FOR THE DEMO:
         * These three far-side signals control the cars entering from the top.
         * Saving each returned Signal lets a Car check its exact light color.
         */
        // West group (horizontal, near y = 652)
        northLeftSignal = arrowSignal(338, 652, "LEFT", Light.YELLOW);
        // This far-side signal controls traffic entering from the north.
        northStraightSignal = circleSignal(405, 652, Light.GREEN);
        northRightSignal = arrowSignal(473, 652, "RIGHT", Light.RED);

        // South group (vertical, near x = 693)
        westLeftSignal = arrowSignal(693, 520, "UP", Light.RED);
        westStraightSignal = circleSignal(693, 590, Light.RED);
        westRightSignal = arrowSignal(693, 660, "DOWN", Light.YELLOW);

        // Public device names used by Multiplexor and the Main test harness.
        signalByName.put("NORTH_LEFT", northLeftSignal);
        signalByName.put("NORTH_STRAIGHT", northStraightSignal);
        signalByName.put("NORTH_RIGHT", northRightSignal);
        signalByName.put("SOUTH_LEFT", southLeftSignal);
        signalByName.put("SOUTH_STRAIGHT", southStraightSignal);
        signalByName.put("SOUTH_RIGHT", southRightSignal);
        signalByName.put("EAST_LEFT", eastLeftSignal);
        signalByName.put("EAST_STRAIGHT", eastStraightSignal);
        signalByName.put("EAST_RIGHT", eastRightSignal);
        signalByName.put("WEST_LEFT", westLeftSignal);
        signalByName.put("WEST_STRAIGHT", westStraightSignal);
        signalByName.put("WEST_RIGHT", westRightSignal);
    }

    private Signal arrowSignal(double cx, double cy, String dir, Light initial) {
        // Build one programmable arrow-shaped LED signal.
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
        // Signal adds the click handler and stores the current light state.
        Signal signal = new Signal(a, initial);
        signals.add(signal);
        add(a);
        return signal;
    }

    private Signal circleSignal(double cx, double cy, Light initial) {
        // Build the programmable round LED used by a straight lane.
        Circle c = new Circle(cx, cy, 25);
        Signal signal = new Signal(c, initial);
        signals.add(signal);
        add(c);
        return signal;
    }

    /**
     * Cars for all three north approach lanes. Each car obeys its matching
     * far-side arrow or round signal before entering the intersection.
     */
    private void trafficSimulation() {
        /*
         * Each route is an ordered list of {x, y} waypoints.
         * Point 0 spawns the car, point 1 is its stop line, the middle points
         * shape a turn, and the final point is beyond the edge of the window.
         */
        List<Car> cars = new ArrayList<>();

        // TOP APPROACH: cars travel down into the intersection.
        cars.add(new Car(new double[][]{{310,-60},{310,140},{310,400},{-80,400}},
                Color.web("#eb5757"), northLeftSignal::isGreen));
        cars.add(new Car(new double[][]{{374,-60},{374,140},{374,H+80}},
                Color.web("#eb5757"), northStraightSignal::isGreen));
        cars.add(new Car(new double[][]{{438,-60},{438,140},{438,520},{W+80,520}},
                Color.web("#eb5757"), northRightSignal::isGreen));

        // BOTTOM APPROACH: cars travel up into the intersection.
        // Each turn is two straight segments, making a simple 90-degree corner.
        cars.add(new Car(new double[][]{{560,H+60},{560,765},{560,400},{-80,400}},
                Color.web("#9b51e0"), southLeftSignal::isGreen));
        cars.add(new Car(new double[][]{{624,H+60},{624,765},{624,-80}},
                Color.web("#9b51e0"), southStraightSignal::isGreen));
        cars.add(new Car(new double[][]{{688,H+60},{688,765},{688,520},{W+80,520}},
                Color.web("#9b51e0"), southRightSignal::isGreen));

        // LEFT APPROACH: cars travel right into the intersection.
        cars.add(new Car(new double[][]{{-60,533},{185,533},{560,533},{560,-80}},
                Color.web("#f2994a"), westLeftSignal::isGreen));
        cars.add(new Car(new double[][]{{-60,580},{185,580},{W+80,580}},
                Color.web("#f2994a"), westStraightSignal::isGreen));
        cars.add(new Car(new double[][]{{-60,627},{185,627},{438,627},{438,H+80}},
                Color.web("#f2994a"), westRightSignal::isGreen));

        // RIGHT APPROACH: cars travel left into the intersection.
        // Exit through the right half of the top road (northbound traffic).
        cars.add(new Car(new double[][]{{W+60,285},{800,285},{560,285},{560,-80}},
                Color.web("#2d9cdb"), eastLeftSignal::isGreen));
        cars.add(new Car(new double[][]{{W+60,345},{800,345},{-80,345}},
                Color.web("#2d9cdb"), eastStraightSignal::isGreen));
        // Exit through the left half of the bottom road (southbound traffic).
        cars.add(new Car(new double[][]{{W+60,405},{800,405},{438,405},{438,H+80}},
                Color.web("#2d9cdb"), eastRightSignal::isGreen));

        add(cars.toArray(new Node[0]));

        // AnimationTimer runs once per JavaFX frame while the window is open.
        new AnimationTimer() {
            private long previousTime;

            @Override
            public void handle(long now) {
                if (previousTime == 0) {
                    previousTime = now;
                    return;
                }

                double elapsedSeconds = (now - previousTime) / 1_000_000_000.0;
                previousTime = now;

                // Pass elapsed time to every car so movement stays smooth.
                for (Car car : cars) {
                    car.update(elapsedSeconds);
                }
            }
        }.start();
    }

    // actual code that turns every coloured signal to red (a pedestrian square or the antenna knob was clicked)
    private void allSignalsRed() {
        for (Signal s : signals) s.forceRed();
    }

    private void activateEmergencyMode() {
        // The antenna represents an emergency command that stops all traffic.
        allSignalsRed();
    }

    /* ---------------- SOCKET / DIGITAL-TWIN COMMANDS ---------------- */

    private void startSocketServer() {
        socketServer = new DigitalTwinServer(5000, this::handleSocketCommand);
        socketServer.start();
    }

    /**
     * Socket work happens on a background thread, but JavaFX objects may only
     * be changed on the JavaFX Application Thread. Platform.runLater performs
     * the command safely and CompletableFuture returns the response afterward.
     */
    private String handleSocketCommand(String command) {
        CompletableFuture<String> response = new CompletableFuture<>();
        Platform.runLater(() -> response.complete(executeCommand(command)));

        try {
            return response.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "ERROR GUI did not process command";
        }
    }

    private String executeCommand(String command) {
        try {
            String[] parts = command.trim().toUpperCase().split("\\s+");

            if (parts.length == 1 && parts[0].equals("PING")) {
                return "OK DIGITAL_TWIN_READY";
            }

            if (parts.length == 3 && parts[0].equals("SET_SIGNAL")) {
                Signal signal = signalByName.get(parts[1]);
                if (signal == null) return "ERROR unknown signal " + parts[1];

                signal.setLight(Light.valueOf(parts[2]));
                return "OK " + parts[1] + " " + parts[2];
            }

            if (parts.length == 3 && parts[0].equals("PEDESTRIAN")) {
                if (!pedestrianByName.containsKey(parts[1])) {
                    return "ERROR unknown pedestrian zone " + parts[1];
                }
                if (!parts[2].equals("ON") && !parts[2].equals("OFF")) {
                    return "ERROR pedestrian state must be ON or OFF";
                }

                setPedestrianAlarm(parts[2].equals("ON"));
                return "OK PEDESTRIAN " + parts[1] + " " + parts[2];
            }

            if (parts.length == 2 && parts[0].equals("ANTENNA")
                    && parts[1].equals("ACTIVATE")) {
                activateEmergencyMode();
                return "OK EMERGENCY_MODE";
            }

            return "ERROR invalid command";
        } catch (IllegalArgumentException e) {
            return "ERROR color must be GREEN, YELLOW, or RED";
        }
    }

    // signal state machine
    private enum Light {
        GREEN, YELLOW, RED;

        // Clicking a signal advances through this testing cycle.
        Light next() {       // green -> yellow -> red -> green
            return values()[(ordinal() + 1) % values().length];
        }

        Color color() {
            return switch (this) {
                case GREEN  -> Crosswalk.GREEN;
                case YELLOW -> Crosswalk.YELLOW;
                case RED    -> Crosswalk.RED;
            };
        }
    }

    // Wraps a shape so a click cycles its color.
    private static final class Signal {
        private Light light;
        private final Shape shape;

        Signal(Shape shape, Light initial) {
            this.shape = shape;
            this.light = initial;
            this.shape.setStroke(Color.web("#00000055"));
            this.shape.setStrokeWidth(1.5);
            this.shape.setCursor(Cursor.HAND);

            // This is what makes every signal clickable during the demonstration.
            this.shape.setOnMouseClicked(e -> {
                light = light.next();
                paint();
            });
            paint();
        }

        private void paint() {
            shape.setFill(light.color());
        }

        void forceRed() {
            light = Light.RED;
            paint();
        }

        void setLight(Light newLight) {
            light = newLight;
            paint();
        }

        boolean isGreen() {
            // Cars call this method before crossing their stop line.
            return light == Light.GREEN;
        }
    }

    // Crossing signal stick figure and box
    private static final class PedZone {
        private final Shape[] parts;

        PedZone(Shape... parts) {
            this.parts = parts;
        }

        void alarm() {
            for (Shape s : parts) {
                s.setStroke(ORANGE);
                if (s instanceof Rectangle r) {
                    r.setFill(Color.web("#ff8c1a33"));
                }
            }
        }

        void clear() {
            for (Shape s : parts) {
                s.setStroke(PAINT);
                if (s instanceof Rectangle r) {
                    r.setFill(Color.TRANSPARENT);
                }
            }
        }
    }

    // just here so this file can also be run directly
    public static void main(String[] args) {
        launch(args);
    }
}
