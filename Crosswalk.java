import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * JavaFX view and digital twin for the Traffic Control System.
 *
 * This class has three main jobs:
 * 1. Draw the intersection and control panel, and own the six simulated
 *    devices from the design diagram (Traffic Lights, Pedestrian Lights,
 *    Pedestrian Call Button, Induction Sensor, Emergency Vehicle Detector,
 *    Day/Night Timer) plus the Power Sensor.
 * 2. Listen on a socket for commands sent by Multiplexor and dispatch each
 *    one to the right device. The socket listener itself lives here too
 *    (there is no separate server class) since Cross Walk is the single box
 *    the diagram shows receiving commands.
 * 3. Animate ordinary cars and the selected emergency-vehicle route.
 *
 * Traffic decisions belong in Controller. This class mainly carries out
 * whatever Controller, through Multiplexor, asks the twin to do.
 * Launch from Main with a JavaFX-bundled JDK, such as Azul Zulu FX.
 */
public class Crosswalk extends Application {

    /*
     * PRESENTATION GUIDE
     * ------------------
     * start()              builds the JavaFX window and every device.
     * executeCommand()     the text protocol parser; one branch per device.
     * TrafficLights.java, PedestrianLights.java, PedestrianCallButton.java,
     * InductionSensor.java, EmergencyVehicleDetector.java, DayNightTimer.java,
     * PowerSensor.java     one file per device shown in the design diagram.
     * Car.java             contains the actual stopping and turning logic.
     */

    // Fixed design size of the intersection. The window scales this pane while
    // preserving its aspect ratio, so all drawing coordinates remain simple.
    private static final double W = 1024;
    private static final double H = 945;

    // Local socket port used for the Multiplexor <-> Cross Walk connection.
    private static final int PORT = 5000;

    // How long a granted pedestrian WALK phase holds all lanes red.
    private static final double WALK_SECONDS = 10;

    // Shared colors keep the road visually consistent with the devices.
    private static final Color BG      = Color.web("#0d0d0d");
    private static final Color PAINT   = Color.web("#f0f0f0"); // white road paint

    // The one true waypoint route for each approach lane. Ordinary cars use
    // these to drive their lane, and an emergency vehicle reuses the exact
    // same route for whichever lane its turn actually needs, so it always
    // travels down a real lane instead of a separately-guessed path.
    private static final Map<String, double[][]> LANE_ROUTES = buildLaneRoutes();

    private static Map<String, double[][]> buildLaneRoutes() {
        Map<String, double[][]> routes = new LinkedHashMap<>();

        // TOP APPROACH: cars travel down into the intersection. The x=325
        // curb lane turns toward WEST (a real right turn); the x=453
        // center-line lane turns toward EAST (a real left turn).
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.NORTH, Multiplexor.Lane.L),
                new double[][]{{438,-60},{438,140},{438,520},{W+80,520}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.NORTH, Multiplexor.Lane.C),
                new double[][]{{374,-60},{374,140},{374,H+80}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.NORTH, Multiplexor.Lane.R),
                new double[][]{{310,-60},{310,140},{310,400},{-80,400}});

        // BOTTOM APPROACH: cars travel up into the intersection.
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.SOUTH, Multiplexor.Lane.L),
                new double[][]{{560,H+60},{560,765},{560,400},{-80,400}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.SOUTH, Multiplexor.Lane.C),
                new double[][]{{624,H+60},{624,765},{624,-80}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.SOUTH, Multiplexor.Lane.R),
                new double[][]{{688,H+60},{688,765},{688,520},{W+80,520}});

        // LEFT APPROACH: cars travel right into the intersection.
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.WEST, Multiplexor.Lane.L),
                new double[][]{{-60,533},{185,533},{560,533},{560,-80}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.WEST, Multiplexor.Lane.C),
                new double[][]{{-60,580},{185,580},{W+80,580}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.WEST, Multiplexor.Lane.R),
                new double[][]{{-60,627},{185,627},{438,627},{438,H+80}});

        // RIGHT APPROACH: cars travel left into the intersection. The y=300
        // curb lane turns toward NORTH (a real right turn); the y=420
        // center-line lane turns toward SOUTH (a real left turn).
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.EAST, Multiplexor.Lane.L),
                new double[][]{{W+60,405},{800,405},{438,405},{438,H+80}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.EAST, Multiplexor.Lane.C),
                new double[][]{{W+60,345},{800,345},{-80,345}});
        routes.put(Multiplexor.laneKey(Multiplexor.Direction.EAST, Multiplexor.Lane.R),
                new double[][]{{W+60,285},{800,285},{560,285},{560,-80}});

        return routes;
    }

    /** The color shared by every lane of one approach's ordinary traffic. */
    private static Color approachColor(Multiplexor.Direction direction) {
        return switch (direction) {
            case NORTH -> Color.web("#eb5757");
            case SOUTH -> Color.web("#9b51e0");
            case WEST  -> Color.web("#f2994a");
            case EAST  -> Color.web("#2d9cdb");
        };
    }

    /**
     * Which lane of an approach actually turns toward a destination, read
     * directly off the lane routes above using the standard driving
     * convention (LEFT crosses opposing through traffic, RIGHT hugs the
     * curb). NORTH and SOUTH are mirror images of each other, as are EAST
     * and WEST; every other pairing is the straight-through C lane.
     */
    private static Multiplexor.Lane laneFor(Multiplexor.Direction approach,
                                            Multiplexor.Direction destination) {
        return switch (approach) {
            case NORTH -> destination == Multiplexor.Direction.EAST ? Multiplexor.Lane.L
                        : destination == Multiplexor.Direction.WEST ? Multiplexor.Lane.R
                        : Multiplexor.Lane.C;
            case SOUTH -> destination == Multiplexor.Direction.WEST ? Multiplexor.Lane.L
                        : destination == Multiplexor.Direction.EAST ? Multiplexor.Lane.R
                        : Multiplexor.Lane.C;
            case EAST -> destination == Multiplexor.Direction.SOUTH ? Multiplexor.Lane.L
                       : destination == Multiplexor.Direction.NORTH ? Multiplexor.Lane.R
                       : Multiplexor.Lane.C;
            case WEST -> destination == Multiplexor.Direction.NORTH ? Multiplexor.Lane.L
                       : destination == Multiplexor.Direction.SOUTH ? Multiplexor.Lane.R
                       : Multiplexor.Lane.C;
        };
    }

    // Every road marking, device, and vehicle is placed on this pane.
    private final Pane root = new Pane();

    // The seven devices from the design diagram. Cross Walk owns each one and
    // is the only class that talks to more than one of them at a time.
    private TrafficLights trafficLights;
    private PedestrianLights pedestrianLights;
    private final PedestrianCallButton pedestrianCallButton = new PedestrianCallButton();
    private InductionSensor inductionSensor;
    private EmergencyVehicleDetector emergencyVehicleDetector;
    private final DayNightTimer dayNightTimer = new DayNightTimer();
    private final PowerSensor powerSensor = new PowerSensor();

    // One simulated car per approach lane, keyed the same way as the traffic
    // lights (Multiplexor.laneKey), so Induction Sensor can find each car.
    private final Map<String, Car> carsByLane = new HashMap<>();

    /**
     * The intersection's single current operating state. Cross Walk is the
     * only class that changes this, and every command handler goes through
     * enterMode(...), so there is one place that decides what "day",
     * "night", "an emergency vehicle is active", and "no power" each mean.
     */
    private enum Mode { STOPPED, DAY, NIGHT, EMERGENCY, NO_POWER }

    private Mode mode = Mode.STOPPED;
    private Mode modeBeforeEmergency = Mode.STOPPED;

    // Drives the automatic signal cycle (green -> yellow -> next pattern).
    // A pending pedestrian request is honored at the next transition instead
    // of cutting the current pattern short.
    private PauseTransition phaseTimer;
    private boolean pedestrianWalkPending = false;

    // Socket transport for the digital-twin protocol. Folded directly into
    // this class instead of a separate server class, since Cross Walk is the
    // single box in the design diagram that receives every command.
    private ServerSocket serverSocket;
    private volatile boolean serverRunning;

    // Communication and animation objects that must be stopped on shutdown.
    private Controller controller;
    private EmergencyVehicle activeEmergencyVehicle;

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

        emergencyVehicleDetector = new EmergencyVehicleDetector(root);

        trafficLights = new TrafficLights(root, name -> sendControllerCommand(
                "Manual signal change: " + name,
                () -> controller.manualSignalChange(name)));

        // Cars are added after the signals so they are visible above the road
        // markings, and each one is registered so Induction Sensor can find it.
        trafficSimulation();
        inductionSensor = new InductionSensor(carsByLane);

        // Pedestrian controls remain visible above moving cars. A click on a
        // corner sign presses the Pedestrian Call Button, the same as the
        // "Request Crossing" panel button does.
        pedestrianLights = new PedestrianLights(root, BG, () -> {
            pedestrianCallButton.press();
            sendControllerCommand("Pedestrian crossing requested",
                    () -> controller.pedestrianRequest());
        });

        // Keep the traffic signals visible when a car passes behind them.
        trafficLights.bringAllToFront();

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
        closeSocketServer();
    }

    /** Builds the right-side buttons, selectors, status area, and mode card. */
    private VBox createSimulationControls() {
        // The server is already listening, so the controller can connect now.
        try {
            controller = new Controller("localhost", PORT);
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
                "Pedestrian crossing requested; will WALK at the next signal change",
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

        Label timingLabel = sectionLabel("SIGNAL TIMING");
        Label timingCaption = fieldLabel("GREEN INTERVAL, SECONDS (YELLOW IS 1/5 OF IT)");
        TextField intervalField = new TextField(
                String.valueOf((int) dayNightTimer.getIntervalSeconds()));
        Button applyInterval = controlButton("Apply Interval", "#334155");
        applyInterval.setOnAction(e -> {
            try {
                double seconds = Double.parseDouble(intervalField.getText().trim());
                if (seconds <= 0) throw new NumberFormatException();
                dayNightTimer.setIntervalSeconds(seconds);
                status.setText("●  Signal interval set to " + seconds
                        + "s (yellow " + (seconds / 5.0) + "s)");
            } catch (NumberFormatException ex) {
                status.setText("●  Enter a positive number of seconds");
            }
        });
        VBox timingCard = card(timingLabel, timingCaption, intervalField, applyInterval);

        VBox header = new VBox(2, title, subtitle);
        VBox panel = new VBox(16, header, status, systemCard,
                emergencyCard, modeCard, timingCard);
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

    /**
     * Cars for all twelve approach lanes. Each car obeys its matching
     * Traffic Lights signal before entering the intersection, and is
     * registered under the same lane key so Induction Sensor can find it.
     */
    private void trafficSimulation() {
        /*
         * Each route is an ordered list of {x, y} waypoints.
         * Point 0 spawns the car, point 1 is its stop line, the middle points
         * shape a turn, and the final point is beyond the edge of the window.
         */
        List<Car> cars = new ArrayList<>();

        for (Map.Entry<String, double[][]> entry : LANE_ROUTES.entrySet()) {
            String key = entry.getKey();
            Multiplexor.Direction direction =
                    Multiplexor.Direction.valueOf(key.substring(0, key.indexOf('_')));
            Car c = new Car(entry.getValue(), approachColor(direction),
                    () -> trafficLights.isGreen(key));
            carsByLane.put(key, c);
            cars.add(c);
        }

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
                for (Car c : cars) {
                    c.update(elapsedSeconds);
                }
            }
        }.start();
    }

    /**
     * The single place every mode transition goes through: cancel whatever
     * was happening, force a safe all-red state, then apply the new mode.
     * DAY resumes the automatic cycle; NIGHT, EMERGENCY, NO_POWER, and
     * STOPPED all simply stay all-red (EMERGENCY_DETECTED greens its own
     * lane right after calling this).
     */
    private void enterMode(Mode newMode) {
        mode = newMode;
        stopSignalCycle();
        setPedestrianWalk(false);
        trafficLights.allRed();
        if (mode == Mode.DAY) {
            startSignalCycle();
        }
    }

    /** Changes the pedestrian devices together and makes traffic all red. */
    private void setPedestrianWalk(boolean active) {
        pedestrianLights.setWalk(active);
        if (active) trafficLights.allRed();
    }

    /* ---------------- AUTOMATIC SIGNAL CYCLE ----------------
     * A basic real intersection does not sit on one pattern forever: it steps
     * through a fixed sequence of green phases, warns with yellow, and moves
     * on. This runs entirely on the JavaFX thread via PauseTransition, so it
     * never needs Platform.runLater the way the socket-driven commands do.
     */

    /** Begins the pattern cycle at its first phase. */
    private void startSignalCycle() {
        runSignalPattern(0);
    }

    /** Cancels any in-flight phase timer and forgets a pending walk request. */
    private void stopSignalCycle() {
        pedestrianWalkPending = false;
        if (phaseTimer != null) {
            phaseTimer.stop();
            phaseTimer = null;
        }
    }

    /** Shows one pattern's green, then its yellow, then moves to the next. */
    private void runSignalPattern(int index) {
        TrafficLights.Pattern[] patterns = TrafficLights.Pattern.values();
        TrafficLights.Pattern pattern = patterns[Math.floorMod(index, patterns.length)];

        trafficLights.showGreen(pattern);
        schedulePhase(dayNightTimer.getIntervalSeconds(), () -> {
            trafficLights.showYellow(pattern);
            schedulePhase(dayNightTimer.getIntervalSeconds() / 5.0, () -> {
                if (pedestrianWalkPending) {
                    pedestrianWalkPending = false;
                    runPedestrianPhase(index);
                } else {
                    runSignalPattern(index + 1);
                }
            });
        });
    }

    /** Holds the pedestrian WALK phase for its full duration, then resumes. */
    private void runPedestrianPhase(int completedIndex) {
        setPedestrianWalk(true);
        schedulePhase(WALK_SECONDS, () -> {
            setPedestrianWalk(false);
            runSignalPattern(completedIndex + 1);
        });
    }

    /** Runs action after a delay, on the JavaFX thread, cancelable via stopSignalCycle(). */
    private void schedulePhase(double seconds, Runnable action) {
        phaseTimer = new PauseTransition(Duration.seconds(seconds));
        phaseTimer.setOnFinished(e -> action.run());
        phaseTimer.play();
    }

    /**
     * Notes a pedestrian WALK request. If the automatic cycle is running, the
     * request waits for the next gap between patterns instead of cutting the
     * current green or yellow phase short; otherwise (night mode, or the
     * system is stopped) there is no cycle to wait for, so it is granted now.
     */
    private void requestPedestrianWalk() {
        if (mode == Mode.DAY && phaseTimer != null) {
            pedestrianWalkPending = true;
        } else {
            setPedestrianWalk(true);
            schedulePhase(WALK_SECONDS, () -> setPedestrianWalk(false));
        }
    }

    /** Replaces any previous emergency animation and starts the chosen route. */
    private void animateEmergencyVehicle(Multiplexor.Direction approach, Multiplexor.Lane lane) {
        if (activeEmergencyVehicle != null) {
            activeEmergencyVehicle.stop();
            root.getChildren().remove(activeEmergencyVehicle);
        }

        double[][] route = LANE_ROUTES.get(Multiplexor.laneKey(approach, lane));
        activeEmergencyVehicle = new EmergencyVehicle(route, () -> {
            root.getChildren().remove(activeEmergencyVehicle);
            activeEmergencyVehicle = null;
            emergencyVehicleDetector.clearActiveApproach();
            enterMode(modeBeforeEmergency);
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
        emergencyVehicleDetector.clearActiveApproach();
    }

    /* ---------------- SOCKET / DIGITAL-TWIN COMMANDS ---------------- */

    /** Opens the port and starts accepting clients on a background thread. */
    private void startSocketServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            serverRunning = true;
        } catch (IOException e) {
            throw new IllegalStateException("Could not open Digital Twin port " + PORT, e);
        }

        Thread serverThread = new Thread(this::acceptClients, "digital-twin-server");
        // A daemon thread will not keep the program alive after JavaFX closes.
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("Digital Twin listening on port " + PORT);
    }

    /** Waits for connections and gives every client its own reader thread. */
    private void acceptClients() {
        while (serverRunning) {
            try {
                Socket client = serverSocket.accept();
                Thread clientThread = new Thread(
                        () -> handleClient(client), "digital-twin-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException e) {
                if (serverRunning) System.err.println("Socket accept error: " + e.getMessage());
            }
        }
    }

    /** Reads one command per line and sends one response per line. */
    private void handleClient(Socket client) {
        // try-with-resources closes the client and streams after disconnect.
        try (Socket socket = client;
             BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

            String command;
            while ((command = input.readLine()) != null) {
                // Multiplexor waits for one response for every command it sends.
                output.println(handleSocketCommand(command));
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }

    /** Stops accepting clients and releases the port. */
    private void closeSocketServer() {
        serverRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // The application is already closing.
            }
        }
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
                enterMode(dayNightTimer.isDay() ? Mode.DAY : Mode.NIGHT);
                return "OK STARTED";
            }

            if (parts.length == 1 && parts[0].equals("RESET")) {
                stopEmergencyVehicle();
                dayNightTimer.set(Multiplexor.Mode.DAY);
                powerSensor.restore();
                enterMode(Mode.STOPPED);
                return "OK RESET";
            }

            if (parts.length == 2 && parts[0].equals("SET_MODE")) {
                if (mode == Mode.EMERGENCY) {
                    return "ERROR emergency vehicle is crossing";
                }
                Multiplexor.Mode dayNightMode;
                try {
                    dayNightMode = Multiplexor.Mode.valueOf(parts[1]);
                } catch (IllegalArgumentException e) {
                    return "ERROR mode must be DAY or NIGHT";
                }
                dayNightTimer.set(dayNightMode);
                if (mode != Mode.STOPPED && mode != Mode.NO_POWER) {
                    enterMode(dayNightMode == Multiplexor.Mode.DAY ? Mode.DAY : Mode.NIGHT);
                }
                return "OK MODE " + dayNightMode;
            }

            // Device API commands used by Multiplexor's five public methods.
            if (parts.length == 1 && parts[0].equals("PED_REQUEST")) {
                return "VALUE " + pedestrianCallButton.testAndClear();
            }

            if (parts.length == 5 && parts[0].equals("SET_TRAFFIC_LIGHT")) {
                if (mode == Mode.EMERGENCY) {
                    return "ERROR emergency vehicle is crossing";
                }

                Multiplexor.Direction direction =
                        Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane lane = Multiplexor.Lane.valueOf(parts[2]);
                Multiplexor.Display display =
                        Multiplexor.Display.valueOf(parts[3]);
                Multiplexor.SignalColor color =
                        Multiplexor.SignalColor.valueOf(parts[4]);

                if (!trafficLights.setOutput(direction, lane, display, color)) {
                    return "ERROR unknown traffic light";
                }
                return "OK TRAFFIC_LIGHT_SET";
            }

            if (parts.length == 2 && parts[0].equals("SET_PED_LIGHT")) {
                if (mode == Mode.EMERGENCY) {
                    return "ERROR emergency vehicle is crossing";
                }
                Multiplexor.PedStatus status =
                        Multiplexor.PedStatus.valueOf(parts[1]);
                if (status == Multiplexor.PedStatus.WALK) {
                    requestPedestrianWalk();
                } else {
                    pedestrianWalkPending = false;
                    if (pedestrianLights.isWalk()) setPedestrianWalk(false);
                }
                return "OK PED_LIGHT " + status;
            }

            if (parts.length == 2 && parts[0].equals("EMERGENCY")) {
                Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[1]);
                return "VALUE " + emergencyVehicleDetector.detect(direction);
            }

            if (parts.length == 3 && parts[0].equals("CAR_DETECTION")) {
                Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane lane = Multiplexor.Lane.valueOf(parts[2]);
                return "VALUE " + inductionSensor.detect(direction, lane);
            }

            // Commands used only by the interactive JavaFX demonstration.
            if (parts.length == 2 && parts[0].equals("CYCLE_SIGNAL")) {
                if (mode == Mode.EMERGENCY) {
                    return "ERROR emergency vehicle is crossing";
                }
                if (!trafficLights.cycle(parts[1])) {
                    return "ERROR unknown signal " + parts[1];
                }
                return "OK CYCLE_SIGNAL " + parts[1];
            }

            if (parts.length == 3 && parts[0].equals("EMERGENCY_DETECTED")) {
                Multiplexor.Direction approach =
                        Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Direction destination =
                        Multiplexor.Direction.valueOf(parts[2]);
                Multiplexor.Lane lane = laneFor(approach, destination);

                if (mode != Mode.EMERGENCY) modeBeforeEmergency = mode;
                enterMode(Mode.EMERGENCY);
                // The emergency vehicle's own lane also goes green, so any
                // ordinary traffic already in it can follow through too.
                trafficLights.setLaneGreen(approach, lane);
                emergencyVehicleDetector.setActiveApproach(approach);
                animateEmergencyVehicle(approach, lane);
                return "OK EMERGENCY_DETECTED " + parts[1] + " " + parts[2];
            }

            if (parts.length == 1 && parts[0].equals("POWER_FAILURE")) {
                stopEmergencyVehicle();
                powerSensor.trip(trafficLights);
                enterMode(Mode.NO_POWER);
                return "OK FAILSAFE";
            }

            return "ERROR invalid command";
        } catch (IllegalArgumentException e) {
            return "ERROR invalid command value";
        }
    }

    // Allows this file to be run directly, although Main is the normal entry point.
    public static void main(String[] args) {
        launch(args);
    }
}
