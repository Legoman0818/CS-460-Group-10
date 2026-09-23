import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
 * JavaFX view and digital twin for the Traffic Control System.
 *
 * This class has three main jobs:
 * 1. Draw the intersection, signals, crosswalks, cars, and control panel.
 * 2. Display state changes requested by Controller through Multiplexor.
 * 3. Animate ordinary cars and the selected emergency-vehicle route.
 *
 * Traffic decisions belong in Controller. Socket transport belongs in
 * Multiplexor and DigitalTwinServer. Crosswalk mainly shows their results.
 * Launch from Main with a JavaFX-bundled JDK, such as Azul Zulu FX.
 */
public class Crosswalk extends Application {

    /*
     * PRESENTATION GUIDE
     * ------------------
     * start()              builds the JavaFX window and calls each drawing method.
     * signals()            creates the programmable traffic lights.
     * trafficSimulation()  creates all twelve cars and runs their animation.
     * diagonalCrosswalk()  draws the X crossing through the intersection.
     * Signal               stores a light's current GREEN/YELLOW/RED state.
     * Car.java             contains the actual stopping and turning logic.
     */

    // Fixed design size of the intersection. The window scales this pane while
    // preserving its aspect ratio, so all drawing coordinates remain simple.
    private static final double W = 1024;
    private static final double H = 945;

    // Shared colors keep the road and all signals visually consistent.
    private static final Color BG      = Color.web("#0d0d0d");
    private static final Color PAINT   = Color.web("#f0f0f0"); // white road paint

    private static final Color GREEN   = Color.web("#3f9e2e");
    private static final Color YELLOW  = Color.web("#f4e017");
    private static final Color RED     = Color.web("#cf1d1d");
    private static final Color ORANGE  = Color.web("#ff8c1a"); // pedestrian alarm state

    // Every road marking, signal, sign, and vehicle is placed on this pane.
    private final Pane root = new Pane();

    // Collections provide quick access when every light or sign must change.
    private final List<Signal> signals = new ArrayList<>();
    private final List<PedZone> pedZones = new ArrayList<>();
    private final Map<String, Signal> signalByName = new HashMap<>();
    // State shown by the digital twin. Controller owns the decision to change
    // it, while this class owns how that state looks on screen.
    private boolean pedAlarm = false;
    private boolean running = false;
    private String operatingMode = "DAY";

    // Communication and animation objects that must be stopped on shutdown.
    private DigitalTwinServer socketServer;
    private Controller controller;
    private EmergencyVehicle activeEmergencyVehicle;

    // Named references connect each lane's car with its programmable signal.
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

    /** Builds the complete window, starts networking, and starts the system. */
    @Override
    public void start(Stage stage) {
        root.setPrefSize(W, H);

        // Draw the intersection one layer at a time. Later items appear on top.
        background();
        stopLines();   
        roads();
        crosswalks();
        laneArrows();
        antenna();
        signals();

        // Cars are added last so they are visible above the road markings.
        trafficSimulation();

        // Pedestrian controls remain visible above moving cars.
        pedestrians();

        // Keep the traffic signals visible when a car passes behind them.
        for (Signal signal : signals) signal.bringToFront();

        // Listen for commands sent by Multiplexor through localhost port 5000.
        startSocketServer();

        
        // Group the intersection separately so only it scales. The controls on
        // the right keep a readable, fixed width when the window is resized.
        Group content = new Group(root);
        Pane frame = new Pane(content);
        frame.setStyle("-fx-background-color: #0d0d0d;");
        content.scaleXProperty().bind(
                javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> Math.min(frame.getWidth() / W, frame.getHeight() / H),
                        frame.widthProperty(), frame.heightProperty()));
        content.scaleYProperty().bind(content.scaleXProperty());

        VBox controls = createSimulationControls();
        BorderPane window = new BorderPane();
        window.setCenter(frame);
        window.setRight(controls);
        BorderPane.setAlignment(controls, Pos.CENTER);

        Scene scene = new Scene(window, W + 290, H, BG);
        stage.setTitle("Crosswalk - Traffic Control System (Group 10)");
        stage.setScene(scene);
        stage.show();

        sendControllerCommand("System started", () -> controller.start());
    }

    /** Releases background threads and sockets when the window closes. */
    @Override
    public void stop() {
        // Release the socket when the JavaFX window closes.
        if (controller != null) {
            try {
                controller.close();
            } catch (IOException ignored) {
                // The application is already closing.
            }
        }
        if (socketServer != null) socketServer.close();
    }

    /** Builds the right-side buttons, selectors, status area, and mode card. */
    private VBox createSimulationControls() {
        // The server is already listening, so the controller can connect now.
        try {
            controller = new Controller("localhost", 5000);
        } catch (IOException e) {
            throw new IllegalStateException("Could not connect Controller", e);
        }

        Label title = new Label("TRAFFIC CONTROL");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; "
                + "-fx-font-weight: bold;");

        Label subtitle = new Label("SIMULATION PANEL");
        subtitle.setStyle("-fx-text-fill: #8fa3b8; -fx-font-size: 11px; "
                + "-fx-font-weight: bold;");

        Label status = new Label("●  System ready");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        status.setPadding(new Insets(10, 12, 10, 12));
        status.setStyle("-fx-text-fill: #a7f3d0; -fx-background-color: #12372f; "
                + "-fx-background-radius: 8px; -fx-font-size: 12px;");

        Label systemLabel = sectionLabel("SYSTEM");
        Button reset = controlButton("Reset System", "#334155");
        reset.setOnAction(e -> sendControllerCommand(
                "System reset to day mode", () -> controller.reset(), status));

        Button powerOff = controlButton("Power Off", "#7f1d1d");
        powerOff.setOnAction(e -> sendControllerCommand(
                "Power failure: failsafe active",
                () -> controller.powerFailure(), status));

        Button pedestrian = controlButton("Request Crossing", "#1e3a5f");
        pedestrian.setOnAction(e -> sendControllerCommand(
                "Pedestrian crossing active for 10 seconds",
                () -> controller.pedestrianRequest(),
                status));

        VBox systemCard = card(systemLabel, reset, powerOff, pedestrian);

        Label emergencyLabel = sectionLabel("EMERGENCY VEHICLE");

        ComboBox<Multiplexor.Direction> from = new ComboBox<>();
        from.getItems().addAll(Multiplexor.Direction.values());
        from.setValue(Multiplexor.Direction.NORTH);
        from.setPrefWidth(108);

        ComboBox<Multiplexor.Direction> to = new ComboBox<>();
        to.getItems().addAll(Multiplexor.Direction.values());
        to.setValue(Multiplexor.Direction.EAST);
        to.setPrefWidth(108);

        Label fromLabel = fieldLabel("FROM");
        Label toLabel = fieldLabel("TO");
        VBox fromBox = new VBox(5, fromLabel, from);
        VBox toBox = new VBox(5, toLabel, to);
        HBox route = new HBox(10, fromBox, toBox);

        Button sendEmergency = controlButton("Send Emergency Vehicle", "#b45309");
        sendEmergency.setOnAction(e -> sendControllerCommand(
                "Emergency route: " + from.getValue() + " to " + to.getValue(),
                () -> controller.emergencyDetected(from.getValue(), to.getValue()),
                status));

        VBox emergencyCard = card(emergencyLabel, route, sendEmergency);

        Label modeLabel = sectionLabel("OPERATING MODE");
        Label modeValue = new Label("DAY MODE");
        modeValue.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; "
                + "-fx-font-weight: bold;");

        Button dayNight = controlButton("Switch to Night", "#334155");
        dayNight.setOnAction(e -> {
            Multiplexor.Mode next = controller.getMode() == Multiplexor.Mode.DAY
                    ? Multiplexor.Mode.NIGHT : Multiplexor.Mode.DAY;
            sendControllerCommand("Mode changed to " + next,
                    () -> controller.setMode(next), status);
            dayNight.setText(next == Multiplexor.Mode.DAY
                    ? "Switch to Night" : "Switch to Day");
            modeValue.setText(next + " MODE");
        });

        VBox modeCard = card(modeLabel, modeValue, dayNight);

        VBox header = new VBox(2, title, subtitle);
        VBox panel = new VBox(16, header, status, systemCard,
                emergencyCard, modeCard);
        panel.setPadding(new Insets(22, 18, 22, 18));
        panel.setPrefWidth(290);
        panel.setMinWidth(290);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setStyle("-fx-background-color: #111827;");
        return panel;
    }

    /** Gives related controls a common card background and spacing. */
    private VBox card(Node... children) {
        VBox box = new VBox(10, children);
        box.setPadding(new Insets(14));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle("-fx-background-color: #1f2937; "
                + "-fx-background-radius: 10px; "
                + "-fx-border-color: #334155; "
                + "-fx-border-radius: 10px;");
        return box;
    }

    /** Creates the small heading used at the top of each control card. */
    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px; "
                + "-fx-font-weight: bold;");
        return label;
    }

    /** Creates labels such as FROM and TO above the route selectors. */
    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        return label;
    }

    /** Creates a consistently sized and styled simulation button. */
    private Button controlButton(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(36);
        button.setStyle("-fx-text-fill: white; -fx-font-size: 12px; "
                + "-fx-font-weight: bold; -fx-background-color: " + color + "; "
                + "-fx-background-radius: 7px; -fx-cursor: hand;");
        return button;
    }

    @FunctionalInterface
    private interface ControllerCommand {
        // Lambdas for buttons can throw IOException without cluttering handlers.
        String run() throws IOException;
    }

    /** Runs a controller command when no status label needs to be updated. */
    private void sendControllerCommand(String success, ControllerCommand command) {
        sendControllerCommand(success, command, null);
    }

    /**
     * Runs socket work away from the JavaFX thread so the window stays
     * responsive. Only the final label update is moved back to JavaFX.
     */
    private void sendControllerCommand(String success, ControllerCommand command,
                                       Label status) {
        Thread worker = new Thread(() -> {
            try {
                command.run();
                if (status != null) {
                    Platform.runLater(() -> status.setText("●  " + success));
                }
            } catch (Exception e) {
                if (status != null) {
                    Platform.runLater(() -> status.setText(
                            "●  " + e.getMessage()));
                }
            }
        }, "controller-command");
        worker.setDaemon(true);
        worker.start();
    }

    /* ---------------- DRAWING HELPERS ---------------- */

    /** Adds one or more JavaFX objects to the intersection pane. */
    private void add(Node... nodes) {
        root.getChildren().addAll(nodes);
    }

    /** Creates a solid white road-marking line. */
    private Line paint(double x1, double y1, double x2, double y2, double w) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(w);
        l.setStrokeLineCap(StrokeLineCap.BUTT);
        return l;
    }

    /** Creates a dashed road-marking line using the shared paint style. */
    private Line dashed(double x1, double y1, double x2, double y2, double w, double on, double off) {
        Line l = paint(x1, y1, x2, y2, w);
        l.getStrokeDashArray().addAll(on, off);
        return l;
    }

    /* ---------------- INTERSECTION LAYERS ----------------
     * These methods draw from the background upward. Their call order in
     * start() controls which objects appear in front of other objects.
     */

    /** Paints the asphalt-colored background across the design area. */
    private void background() {
        Rectangle bg = new Rectangle(0, 0, W, H);
        bg.setFill(BG);
        add(bg);
    }

    /** Draws road boundaries, center lines, and the twelve approach lanes. */
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

    /** Draws four outside crosswalks and the X-shaped center crossing. */
    private void crosswalks() {
        crosswalkTicks(318, 709, 200, 232, true);   // top
        crosswalkTicks(318, 709, 724, 756, true);   // bottom
        crosswalkTicks(246, 272, 292, 686, false);  // left
        crosswalkTicks(762, 788, 292, 686, false);  // right

        // New diagonal pedestrian crossing requested for the center.
        diagonalCrosswalk();
    }

    /** Draws two striped diagonal paths that form an X through the middle. */
    private void diagonalCrosswalk() {
        diagonalCrosswalkStripe(315, 265, 713, 695);
        diagonalCrosswalkStripe(713, 265, 315, 695);
    }

    private void diagonalCrosswalkStripe(double x1, double y1,
                                         double x2, double y2) {
        // A perpendicular vector creates short stripes across the diagonal.
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        double px = -dy / length;
        double py = dx / length;

        int stripeCount = 23;
        double halfStripe = 12;
        for (int i = 0; i <= stripeCount; i++) {
            double t = i / (double) stripeCount;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            add(paint(x - px * halfStripe, y - py * halfStripe,
                      x + px * halfStripe, y + py * halfStripe, 4));
        }
    }

    // Draws a ladder-style crosswalk. When vertical is true, the individual
    // ticks are vertical and spread across the x direction.
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

    /** Draws the four thick lines where approaching cars must stop. */
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
        // Build the arrowhead from two points perpendicular to the direction.
        double s = 13;
        double bx = tx - dx * s, by = ty - dy * s;
        double px = -dy, py = dx; // perpendicular
        p.getElements().add(new MoveTo(bx + px * s, by + py * s));
        p.getElements().add(new LineTo(tx, ty));
        p.getElements().add(new LineTo(bx - px * s, by - py * s));
    }

    // pedestrian crossing signs
    // One square per corner with a stick figure 
    // Each square displays the state of its pedestrian crossing.
    /** Creates the four corner signs and the protected center pedestrian sign. */
    private void pedestrians() {
        pedestrianZone("NW", 250, 198);   // north-west corner
        pedestrianZone("NE", 783, 210);   // north-east corner
        pedestrianZone("SW", 250, 765);   // south-west corner
        pedestrianZone("SE", 785, 765);   // south-east corner
        // The center control uses a larger, solid panel so the white diagonal
        // crosswalk stripes cannot show through and hide the stick figure.
        pedestrianZone("CENTER", 514, 480, true);
    }

    /** Convenience overload used by normal corner pedestrian signs. */
    private void pedestrianZone(String name, double cx, double cy) {
        pedestrianZone(name, cx, cy, false);
    }

    /** Builds one pedestrian sign from a box and a simple stick figure. */
    private void pedestrianZone(String name, double cx, double cy,
                                boolean centerControl) {
        // All five pedestrian signs use the same 52-by-52 size.
        double half = 26;
        Rectangle box = new Rectangle(cx - half, cy - half, half * 2, half * 2);
        // The center gets a road-colored backing that hides the X stripes.
        // Because it matches the road, it still looks like the corner signs.
        Color normalFill = centerControl ? BG : Color.TRANSPARENT;
        box.setFill(normalFill);
        box.setStroke(PAINT);
        box.setStrokeWidth(2);
        box.setPickOnBounds(true);        

        double scale = 1.0;
        // Every sign starts with the same white figure. alarm() changes every
        // control to orange when a pedestrian control is pressed.
        Color figureColor = PAINT;
        Circle head = new Circle(cx, cy - 11 * scale, 6 * scale);
        head.setStroke(figureColor);
        head.setStrokeWidth(2);
        head.setFill(Color.TRANSPARENT);
        Line body = strokeLine(cx, cy - 5 * scale, cx, cy + 6 * scale);
        Line arms = strokeLine(cx - 8 * scale, cy - scale,
                               cx + 8 * scale, cy - scale);
        Line legL = strokeLine(cx, cy + 6 * scale,
                               cx - 7 * scale, cy + 17 * scale);
        Line legR = strokeLine(cx, cy + 6 * scale,
                               cx + 7 * scale, cy + 17 * scale);
        Group g = new Group(box, head, body, arms, legL, legR);
        PedZone zone = new PedZone(normalFill, figureColor,
                                   box, head, body, arms, legL, legR);
        pedZones.add(zone);
        add(g);
        if (centerControl) {
            // Guarantee that cars, road markings, and signals cannot cover it.
            g.toFront();
        }
    }

    /** Changes every pedestrian sign together and makes traffic all red. */
    private void setPedestrianAlarm(boolean active) {
        pedAlarm = active;
        for (PedZone zone : pedZones) {
            if (active) zone.alarm();
            else zone.clear();
        }
        if (active) allSignalsRed();
    }

    /** Creates the rounded white lines used to build pedestrian figures. */
    private Line strokeLine(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(2);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    /** Draws the antenna symbol representing emergency-vehicle detection. */
    private void antenna() {
        add(strokeLine(823, 178, 823, 197));
        Text label = new Text(834, 201, "Antenna");
        label.setFill(PAINT);
        label.setFont(Font.font(12));

        Circle knob = new Circle(823, 172, 6);
        knob.setFill(PAINT);
        knob.setStroke(RED);
        knob.setStrokeWidth(1.5);
        add(label, knob);
    }

    // Creates all twelve programmable signals and assigns one to each lane.
    private void signals() {
        /*
         * Every signal is directly in front of the cars it controls.
         * Each signal sits just beyond the stop line and aligns with its lane.
         */

        // NORTH/TOP approach: cars travel down toward y = 198.
        northLeftSignal = arrowSignal(325, 270, "LEFT", Light.YELLOW);
        northStraightSignal = circleSignal(389, 270, Light.GREEN);
        northRightSignal = arrowSignal(453, 270, "RIGHT", Light.RED);

        // SOUTH/BOTTOM approach: cars travel up toward y = 758.
        southLeftSignal = arrowSignal(575, 690, "LEFT", Light.RED);
        southStraightSignal = circleSignal(639, 690, Light.GREEN);
        southRightSignal = arrowSignal(703, 690, "RIGHT", Light.YELLOW);

        // WEST/LEFT approach: cars travel right toward x = 244.
        // These use 60-pixel spacing so the large LED symbols do not touch.
        westLeftSignal = arrowSignal(300, 535, "UP", Light.RED);
        westStraightSignal = circleSignal(300, 595, Light.RED);
        westRightSignal = arrowSignal(300, 655, "DOWN", Light.YELLOW);

        // EAST/RIGHT approach: cars travel left toward x = 790.
        eastLeftSignal = arrowSignal(728, 300, "UP", Light.YELLOW);
        eastStraightSignal = circleSignal(728, 360, Light.RED);
        eastRightSignal = arrowSignal(728, 420, "DOWN", Light.RED);

        registerSignal("NORTH_LEFT", northLeftSignal);
        registerSignal("NORTH_STRAIGHT", northStraightSignal);
        registerSignal("NORTH_RIGHT", northRightSignal);
        registerSignal("SOUTH_LEFT", southLeftSignal);
        registerSignal("SOUTH_STRAIGHT", southStraightSignal);
        registerSignal("SOUTH_RIGHT", southRightSignal);
        registerSignal("EAST_LEFT", eastLeftSignal);
        registerSignal("EAST_STRAIGHT", eastStraightSignal);
        registerSignal("EAST_RIGHT", eastRightSignal);
        registerSignal("WEST_LEFT", westLeftSignal);
        registerSignal("WEST_STRAIGHT", westStraightSignal);
        registerSignal("WEST_RIGHT", westRightSignal);

    }

    /** Stores a signal by name and makes its JavaFX shape clickable. */
    private void registerSignal(String name, Signal signal) {
        signalByName.put(name, signal);
        signal.shape.setCursor(Cursor.HAND);
        signal.shape.setOnMouseClicked(e -> sendControllerCommand(
                "Manual signal change: " + name,
                () -> controller.manualSignalChange(name)));
    }

    /** Builds an arrow-shaped programmable LED signal. */
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
        Signal signal = new Signal(a, initial);
        signals.add(signal);
        add(a);
        return signal;
    }

    /** Builds a round programmable LED signal for a straight lane. */
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
    /** Creates the twelve looping cars and advances them every JavaFX frame. */
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

    // Places the intersection in a safe all-red state.
    private void allSignalsRed() {
        for (Signal s : signals) s.forceRed();
    }

    /** Restores the default day pattern, or all red when not in day mode. */
    private void applyNormalPattern() {
        setPedestrianAlarm(false);
        allSignalsRed();
        if (running && operatingMode.equals("DAY")) {
            northStraightSignal.setLight(Light.GREEN);
            southStraightSignal.setLight(Light.GREEN);
        }
    }

    /** Replaces any previous emergency animation and starts the chosen route. */
    private void animateEmergencyVehicle(Multiplexor.Direction approach,
                                         Multiplexor.Direction destination) {
        if (activeEmergencyVehicle != null) {
            activeEmergencyVehicle.stop();
            root.getChildren().remove(activeEmergencyVehicle);
        }

        activeEmergencyVehicle = new EmergencyVehicle(
                emergencyRoute(approach, destination), () -> {
                    root.getChildren().remove(activeEmergencyVehicle);
                    activeEmergencyVehicle = null;
                    applyNormalPattern();
                });
        add(activeEmergencyVehicle);
        activeEmergencyVehicle.toFront();
        activeEmergencyVehicle.play();
    }

    /** Stops and removes an emergency vehicle during reset or power failure. */
    private void stopEmergencyVehicle() {
        if (activeEmergencyVehicle != null) {
            activeEmergencyVehicle.stop();
            root.getChildren().remove(activeEmergencyVehicle);
            activeEmergencyVehicle = null;
        }
    }

    /**
     * Builds the waypoint list for one emergency route.
     *
     * Opposite-side routes remain straight in their travel lane. Turning
     * routes combine the entry lane and exit lane at one corner, producing two
     * perpendicular segments instead of a diagonal path through the center.
     */
    private double[][] emergencyRoute(Multiplexor.Direction approach,
                                      Multiplexor.Direction destination) {
        // Straight routes stay in the travel lane instead of cutting to the
        // exact center and then cutting back out.
        if (approach == Multiplexor.Direction.NORTH
                && destination == Multiplexor.Direction.SOUTH) {
            return new double[][]{
                {389, -70}, {389, 198}, {389, 713}, {389, H + 80}
            };
        }
        if (approach == Multiplexor.Direction.SOUTH
                && destination == Multiplexor.Direction.NORTH) {
            return new double[][]{
                {639, H + 70}, {639, 758}, {639, 246}, {639, -80}
            };
        }
        if (approach == Multiplexor.Direction.WEST
                && destination == Multiplexor.Direction.EAST) {
            return new double[][]{
                {-70, 595}, {244, 595}, {735, 595}, {W + 80, 595}
            };
        }
        if (approach == Multiplexor.Direction.EAST
                && destination == Multiplexor.Direction.WEST) {
            return new double[][]{
                {W + 70, 360}, {790, 360}, {293, 360}, {-80, 360}
            };
        }

        double[][] entry = switch (approach) {
            case NORTH -> new double[][]{{389, -70}, {389, 198}};
            case SOUTH -> new double[][]{{639, H + 70}, {639, 758}};
            case EAST  -> new double[][]{{W + 70, 360}, {790, 360}};
            case WEST  -> new double[][]{{-70, 595}, {244, 595}};
        };

        double[] exit = switch (destination) {
            case NORTH -> new double[]{560, -80};
            case SOUTH -> new double[]{438, H + 80};
            case EAST  -> new double[]{W + 80, 520};
            case WEST  -> new double[]{-80, 400};
        };

        // Keep one coordinate from each lane to create a 90-degree corner.
        boolean enteringVertically = approach == Multiplexor.Direction.NORTH
                || approach == Multiplexor.Direction.SOUTH;
        double[] corner = enteringVertically
                ? new double[]{entry[1][0], exit[1]}
                : new double[]{exit[0], entry[1][1]};

        return new double[][]{entry[0], entry[1], corner, exit};
    }

    /* ---------------- SOCKET / DIGITAL-TWIN COMMANDS ---------------- */

    private void startSocketServer() {
        // Crosswalk supplies the command callback; the server handles sockets.
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

    /**
     * Parses the small line-based protocol used by Multiplexor. Every valid
     * command returns one OK or VALUE response. Invalid input returns ERROR so
     * the server thread never crashes because of a malformed command.
     */
    private String executeCommand(String command) {
        try {
            // Uppercase input makes the protocol case-insensitive.
            String[] parts = command.trim().toUpperCase().split("\\s+");

            // System lifecycle commands.
            if (parts.length == 1 && parts[0].equals("START")) {
                running = true;
                applyNormalPattern();
                return "OK STARTED";
            }

            if (parts.length == 1 && parts[0].equals("RESET")) {
                stopEmergencyVehicle();
                running = false;
                operatingMode = "DAY";
                setPedestrianAlarm(false);
                allSignalsRed();
                return "OK RESET";
            }

            if (parts.length == 2 && parts[0].equals("SET_MODE")) {
                if (activeEmergencyVehicle != null) {
                    return "ERROR emergency vehicle is crossing";
                }
                if (!parts[1].equals("DAY") && !parts[1].equals("NIGHT")) {
                    return "ERROR mode must be DAY or NIGHT";
                }
                operatingMode = parts[1];
                applyNormalPattern();
                return "OK MODE " + operatingMode;
            }

            // Device API commands used by Multiplexor's five public methods.
            if (parts.length == 1 && parts[0].equals("PED_REQUEST")) {
                return "VALUE " + pedAlarm;
            }

            if (parts.length == 5 && parts[0].equals("SET_TRAFFIC_LIGHT")) {
                if (activeEmergencyVehicle != null) {
                    return "ERROR emergency vehicle is crossing";
                }

                Multiplexor.Direction direction =
                        Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane lane = Multiplexor.Lane.valueOf(parts[2]);
                Multiplexor.Display display =
                        Multiplexor.Display.valueOf(parts[3]);
                Multiplexor.SignalColor color =
                        Multiplexor.SignalColor.valueOf(parts[4]);

                Signal signal = signalByName.get(signalName(direction, lane));
                if (signal == null) return "ERROR unknown traffic light";
                signal.setOutput(display, color);
                return "OK TRAFFIC_LIGHT_SET";
            }

            if (parts.length == 2 && parts[0].equals("SET_PED_LIGHT")) {
                if (activeEmergencyVehicle != null) {
                    return "ERROR emergency vehicle is crossing";
                }
                Multiplexor.PedStatus status =
                        Multiplexor.PedStatus.valueOf(parts[1]);
                if (status == Multiplexor.PedStatus.WALK) {
                    setPedestrianAlarm(true);
                } else {
                    applyNormalPattern();
                }
                return "OK PED_LIGHT " + status;
            }

            if (parts.length == 2 && parts[0].equals("EMERGENCY")) {
                Multiplexor.Direction.valueOf(parts[1]);
                return "VALUE TRUE";
            }

            if (parts.length == 3 && parts[0].equals("CAR_DETECTION")) {
                Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane.valueOf(parts[2]);
                return "VALUE TRUE";
            }

            // Commands used only by the interactive JavaFX demonstration.
            if (parts.length == 2 && parts[0].equals("CYCLE_SIGNAL")) {
                if (activeEmergencyVehicle != null) {
                    return "ERROR emergency vehicle is crossing";
                }
                Signal signal = signalByName.get(parts[1]);
                if (signal == null) {
                    return "ERROR unknown signal " + parts[1];
                }
                signal.nextLight();
                return "OK CYCLE_SIGNAL " + parts[1];
            }

            if (parts.length == 3 && parts[0].equals("EMERGENCY_DETECTED")) {
                Multiplexor.Direction approach =
                        Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Direction destination =
                        Multiplexor.Direction.valueOf(parts[2]);
                allSignalsRed();
                animateEmergencyVehicle(approach, destination);
                return "OK EMERGENCY_DETECTED " + parts[1] + " " + parts[2];
            }

            if (parts.length == 1 && parts[0].equals("POWER_FAILURE")) {
                stopEmergencyVehicle();
                running = false;
                setPedestrianAlarm(false);
                allSignalsRed();
                return "OK FAILSAFE";
            }

            return "ERROR invalid command";
        } catch (IllegalArgumentException e) {
            return "ERROR invalid command value";
        }
    }

    /** Converts a direction and short lane enum into the registered GUI name. */
    private String signalName(Multiplexor.Direction direction,
                              Multiplexor.Lane lane) {
        String laneName = switch (lane) {
            case L -> "LEFT";
            case R -> "RIGHT";
            case C -> "STRAIGHT";
        };
        return direction + "_" + laneName;
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
                case GREEN  -> Crosswalk.GREEN;
                case YELLOW -> Crosswalk.YELLOW;
                case RED    -> Crosswalk.RED;
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

        /** Updates the JavaFX shape to match the stored logical color. */
        private void paint() {
            shape.setFill(light.color());
        }

        /** Failsafe operation used for crossings, emergencies, and power loss. */
        void forceRed() {
            light = Light.RED;
            shape.setOpacity(1.0);
            paint();
        }

        /** Sets one signal color directly and ensures it is visible. */
        void setLight(Light newLight) {
            light = newLight;
            shape.setOpacity(1.0);
            paint();
        }

        /** Applies a command received through the agreed traffic-light API. */
        void setOutput(Multiplexor.Display display,
                       Multiplexor.SignalColor color) {
            // Lane choice determines the existing shape; OFF dims that shape.
            if (display == Multiplexor.Display.OFF) {
                shape.setOpacity(0.15);
                return;
            }
            shape.setOpacity(1.0);
            setLight(Light.valueOf(color.name()));
        }

        /** Cycles this signal when its shape is clicked in the simulation. */
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

    // Stores the shapes that form one pedestrian sign and recolors them as a group.
    private static final class PedZone {
        private final Color normalFill;
        private final Color normalStroke;
        private final Shape[] parts;

        PedZone(Color normalFill, Color normalStroke, Shape... parts) {
            this.normalFill = normalFill;
            this.normalStroke = normalStroke;
            this.parts = parts;
        }

        /** Shows the active crossing state in orange. */
        void alarm() {
            for (Shape s : parts) {
                s.setStroke(ORANGE);
                if (s instanceof Rectangle r) {
                    // Keep the center island opaque even while its alarm is on.
                    r.setFill(normalFill.equals(Color.TRANSPARENT)
                              ? Color.web("#ff8c1a33")
                              : Color.web("#5a2a00"));
                }
            }
        }

        /** Restores the normal white pedestrian sign. */
        void clear() {
            for (Shape s : parts) {
                s.setStroke(normalStroke);
                if (s instanceof Rectangle r) {
                    r.setFill(normalFill);
                }
            }
        }
    }

    // Allows this file to be run directly, although Main is the normal entry point.
    public static void main(String[] args) {
        launch(args);
    }
}
