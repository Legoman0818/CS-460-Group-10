import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Crosswalk - the Digital Twin, our fake intersection. (Walkthrough 3.5)
 *
 * Design Diagram: not drawn as a box. It holds all the device boxes at the
 * bottom of the diagram (Traffic Lights, Pedestrian, Induction Sensor,
 * Emergency Vehicle Detector).
 *
 * API: this is the twin's side of the API. executeCommand() answers every
 * command the Multiplexor sends.
 *
 * It does NOT make any traffic decisions, the Controller does that. It has 4 jobs:
 *   Job 1: build the intersection and the window   (start)
 *   Job 2: move the cars                           (addCars)
 *   Job 3: the side panel                          (createPanel and the panel actions)
 *   Job 4: the server that answers the Multiplexor (startServer ... executeCommand)
 */
public class Crosswalk extends Application {

    static final int PORT = 5000; // the Multiplexor connects to this

    // the intersection picture, everything on the road gets drawn on this
    private final Pane root = new Pane();

    // the devices (the boxes at the bottom of the Design Diagram, plus the timer and power sensor)
    private TrafficLights trafficLights;
    private Pedestrian pedestrian;
    private InductionSensor inductionSensor;
    private EmergencyVehicleDetector emergencyVehicleDetector;
    private final DayNightTimer dayNightTimer = new DayNightTimer();
    private final PowerSensor powerSensor = new PowerSensor();

    // one car per lane, keys look like "NORTH_LEFT"
    private final Map<String, Car> carsByLane = new HashMap<>();
    private EmergencyVehicle activeEmergencyVehicle;

    // the server (Job 4)
    private ServerSocket serverSocket;
    private Socket client; // the Controller's connection

    // panel labels/buttons that get changed from more than one place
    private Label status;
    private Label modeValue;
    private Button dayNight;

    // ---------------- Job 1: build the intersection ----------------

    /**
     * JavaFX calls this when the window opens. Draws the roads, makes each
     * device, adds the cars, builds the side panel, shows the window and
     * starts the server.
     */
    @Override
    public void start(Stage stage) {
        root.setPrefSize(Roads.W, Roads.H);
        new Roads(root);

        emergencyVehicleDetector = new EmergencyVehicleDetector(root);
        trafficLights = new TrafficLights(root, this::manualSignalChange);

        // the order matters: cars go on top of the road but under the lights
        addCars();
        inductionSensor = new InductionSensor(carsByLane);

        pedestrian = new Pedestrian(root, () -> showStatus("Pedestrian crossing requested"));
        trafficLights.bringAllToFront();

        // Resizing: only the intersection zooms when the window is resized,
        // the panel on the right stays the same size. The zoom is whichever
        // of width/height runs out first, so the whole intersection always
        // fits and never gets stretched.
        root.setMinSize(Roads.W, Roads.H); // the picture itself never changes size,
        root.setMaxSize(Roads.W, Roads.H); // it only zooms
        StackPane frame = new StackPane(root); // StackPane keeps it centered
        frame.setMinSize(0, 0); // lets the window get smaller than the full size picture
        frame.setStyle("-fx-background-color: #0d0d0d;");
        root.scaleXProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(frame.getWidth() / Roads.W, frame.getHeight() / Roads.H),
                frame.widthProperty(), frame.heightProperty()));
        root.scaleYProperty().bind(root.scaleXProperty());

        // the panel scrolls if the window is too short to show all of it (it's about 800 px tall)
        ScrollPane panelScroll = new ScrollPane(createPanel());
        panelScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        panelScroll.setStyle("-fx-background: #111827; -fx-background-color: #111827; -fx-padding: 0;");

        BorderPane window = new BorderPane();
        window.setCenter(frame);
        window.setRight(panelScroll);

        // Start at full size, or smaller if the screen isn't big enough.
        // 40 is left for the window's title bar.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(Roads.W + 290, screen.getWidth());
        double height = Math.min(Roads.H, screen.getHeight() - 40);

        stage.setTitle("Crosswalk - Traffic Control System (Group 10)");
        stage.setScene(new Scene(window, width, height, Roads.BG));
        stage.show();

        startServer();
    }

    /** JavaFX calls this when the window closes. */
    @Override
    public void stop() {
        closeServer(); // closing the connection is also what makes Controller.run() stop
    }

    // ---------------- Job 2: move the cars ----------------

    /**
     * Makes one Car for every route in Roads.ROUTES and keeps them in
     * carsByLane (the Induction Sensor looks cars up in there). Then starts
     * a timer that moves every car each frame.
     */
    private void addCars() {
        List<Car> cars = new ArrayList<>();
        for (Map.Entry<String, double[][]> entry : Roads.ROUTES.entrySet()) {
            String lane = entry.getKey();
            String direction = lane.substring(0, lane.indexOf('_'));
            Car car = new Car(entry.getValue(), carColor(direction), trafficLights, lane);
            carsByLane.put(lane, car);
            cars.add(car);
        }
        root.getChildren().addAll(cars);

        // AnimationTimer runs handle() every frame, about 60 times a second
        new AnimationTimer() {
            private long previousTime;

            @Override
            public void handle(long now) {
                if (previousTime == 0) {
                    previousTime = now;
                    return;
                }
                double seconds = (now - previousTime) / 1_000_000_000.0; // now is in nanoseconds, so change it to seconds
                previousTime = now;
                for (Car car : cars) {
                    car.update(seconds);
                }
            }
        }.start();
    }

    /** Each direction's cars get their own color. */
    private static Color carColor(String direction) {
        return switch (direction) {
            case "NORTH" -> Color.web("#eb5757");
            case "SOUTH" -> Color.web("#9b51e0");
            case "WEST"  -> Color.web("#f2994a");
            default      -> Color.web("#2d9cdb");
        };
    }

    // ---------------- Job 3: the side panel ----------------

    /** Builds the buttons on the right. What each button does is in the panel actions below. */
    private VBox createPanel() {
        Label title = new Label("TRAFFIC CONTROL");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label subtitle = new Label("SIMULATION PANEL");
        subtitle.setStyle("-fx-text-fill: #8fa3b8; -fx-font-size: 11px; -fx-font-weight: bold;");

        status = new Label("●  System ready");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        status.setPadding(new Insets(10, 12, 10, 12));
        status.setStyle("-fx-text-fill: #a7f3d0; -fx-background-color: #12372f; "
                + "-fx-background-radius: 8px; -fx-font-size: 12px;");

        // SYSTEM card: Reset, Power Off, Request Crossing
        Button reset = button("Reset System", "#334155");
        reset.setOnAction(e -> resetSystem());
        Button powerOff = button("Power Off", "#7f1d1d");
        powerOff.setOnAction(e -> powerFailure());
        Button requestCrossing = button("Request Crossing", "#1e3a5f");
        requestCrossing.setOnAction(e -> requestCrossing());
        VBox systemCard = card(sectionLabel("SYSTEM"), reset, powerOff, requestCrossing);

        // EMERGENCY VEHICLE card: pick where it comes from and where it goes
        ComboBox<Multiplexor.Direction> from = new ComboBox<>();
        from.getItems().addAll(Multiplexor.Direction.values());
        from.setValue(Multiplexor.Direction.NORTH);
        from.setPrefWidth(108);
        ComboBox<Multiplexor.Direction> to = new ComboBox<>();
        to.getItems().addAll(Multiplexor.Direction.values());
        to.setValue(Multiplexor.Direction.EAST);
        to.setPrefWidth(108);
        HBox route = new HBox(10,
                new VBox(5, fieldLabel("FROM"), from),
                new VBox(5, fieldLabel("TO"), to));
        Button sendEmergency = button("Send Emergency Vehicle", "#b45309");
        sendEmergency.setOnAction(e -> sendEmergencyVehicle(from.getValue(), to.getValue()));
        VBox emergencyCard = card(sectionLabel("EMERGENCY VEHICLE"), route, sendEmergency);

        // DAY/NIGHT TIMER card
        modeValue = new Label();
        modeValue.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");
        dayNight = button("", "#334155");
        dayNight.setOnAction(e -> toggleDayNight());
        updateModeCard();
        VBox modeCard = card(sectionLabel("DAY/NIGHT TIMER"), modeValue, dayNight);

        // SIGNAL TIMING card: the day green time (yellow is 1/5 of it)
        TextField intervalField = new TextField(
                String.valueOf((int) dayNightTimer.getIntervalSeconds()));
        Button applyInterval = button("Apply Interval", "#334155");
        applyInterval.setOnAction(e -> {
            try {
                double seconds = Double.parseDouble(intervalField.getText().trim());
                if (seconds <= 0) throw new NumberFormatException();
                dayNightTimer.setIntervalSeconds(seconds);
                showStatus("Signal interval set to " + seconds
                        + "s (yellow " + (seconds / 5.0) + "s)");
            } catch (NumberFormatException ex) {
                showStatus("Enter a positive number of seconds");
            }
        });
        VBox timingCard = card(sectionLabel("SIGNAL TIMING"),
                fieldLabel("GREEN INTERVAL, SECONDS (YELLOW IS 1/5 OF IT)"),
                intervalField, applyInterval);

        VBox panel = new VBox(16, new VBox(2, title, subtitle), status,
                systemCard, emergencyCard, modeCard, timingCard);
        panel.setPadding(new Insets(22, 18, 22, 18));
        panel.setPrefWidth(290);
        panel.setMinWidth(290);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setStyle("-fx-background-color: #111827;");
        return panel;
    }

    // the next few just style the panel so createPanel() isn't so long

    private VBox card(Node... children) {
        VBox box = new VBox(10, children);
        box.setPadding(new Insets(14));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle("-fx-background-color: #1f2937; -fx-background-radius: 10px; "
                + "-fx-border-color: #334155; -fx-border-radius: 10px;");
        return box;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 10px;");
        return label;
    }

    private Button button(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(36);
        button.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; "
                + "-fx-background-color: " + color + "; -fx-background-radius: 7px; "
                + "-fx-cursor: hand;");
        return button;
    }

    // ---------------- Job 3: panel actions ----------------
    // These ONLY change the devices. They never call the Controller.
    // The Controller sees the change the next time it checks (every 100 ms).

    /** Shows a message in the green status box at the top of the panel. */
    private void showStatus(String message) {
        if (status != null) status.setText("●  " + message);
    }

    /** Reset System: no emergency vehicle, no button press, DAY mode, power back on. */
    private void resetSystem() {
        removeEmergencyVehicle();
        pedestrian.clearRequest();
        dayNightTimer.set(Mode.DAY);
        powerSensor.restore(trafficLights);
        updateModeCard();
        showStatus("System reset to day mode");
    }

    /** Power Off: the power sensor makes the lights blink red (see PowerSensor.trip). */
    private void powerFailure() {
        removeEmergencyVehicle();
        powerSensor.trip(trafficLights);
        pedestrian.setWalkLight(false);
        showStatus("Power failure: failsafe active");
    }

    /** Request Crossing: same as clicking one of the corner signs. */
    private void requestCrossing() {
        if (!powerSensor.isOn()) {
            showStatus("Power is off. Reset the system first.");
            return;
        }
        pedestrian.press();
        showStatus("Pedestrian crossing requested; will WALK at the next signal change");
    }

    /** Send Emergency Vehicle: tells the detector the direction, then starts the ambulance. */
    private void sendEmergencyVehicle(Multiplexor.Direction from, Multiplexor.Direction to) {
        if (!powerSensor.isOn()) {
            showStatus("Power is off. Reset the system first.");
            return;
        }
        if (from == to) {
            showStatus("FROM and TO must be different");
            return;
        }
        removeEmergencyVehicle();
        emergencyVehicleDetector.setActiveApproach(from);

        // use the route of the normal car in the lane that turns the right way
        double[][] route = Roads.ROUTES.get(Multiplexor.laneKey(from, Roads.laneFor(from, to)));
        activeEmergencyVehicle = new EmergencyVehicle(route, this::removeEmergencyVehicle);
        root.getChildren().add(activeEmergencyVehicle);
        activeEmergencyVehicle.play();
        showStatus("Emergency route: " + from + " to " + to);
    }

    /**
     * Removes the emergency vehicle (if there is one) and clears the detector.
     * The ambulance also calls this itself when it drives off the screen.
     */
    private void removeEmergencyVehicle() {
        if (activeEmergencyVehicle != null) {
            activeEmergencyVehicle.stop();
            root.getChildren().remove(activeEmergencyVehicle);
            activeEmergencyVehicle = null;
        }
        emergencyVehicleDetector.clearActiveApproach();
    }

    /** Switch to Night/Day: flips the Day/Night Timer. */
    private void toggleDayNight() {
        dayNightTimer.set(dayNightTimer.isDay() ? Mode.NIGHT : Mode.DAY);
        updateModeCard();
        showStatus("Day/Night Timer set to " + dayNightTimer.get());
    }

    /** Updates the DAY/NIGHT TIMER card's label and button text. */
    private void updateModeCard() {
        modeValue.setText(dayNightTimer.get() + " MODE");
        dayNight.setText(dayNightTimer.isDay() ? "Switch to Night" : "Switch to Day");
    }

    /**
     * Clicking a traffic light changes its color (just for the demo).
     * The Controller sets it back the next time it changes the lights.
     */
    private void manualSignalChange(String name) {
        if (!powerSensor.isOn()) return;
        trafficLights.cycle(name);
        showStatus("Manual signal change: " + name);
    }

    // ---------------- Job 4: the server ----------------

    /** Opens port 5000 and starts one background thread to run the server. */
    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            throw new IllegalStateException("Could not open port " + PORT, e);
        }

        // setDaemon(true) means this thread won't keep the program open after the window closes
        Thread serverThread = new Thread(this::runServer, "twin-server");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("Digital Twin listening on port " + PORT);
    }

    /** Waits for the Controller to connect, then answers it until it disconnects. Then waits again. */
    private void runServer() {
        try {
            while (true) {
                client = serverSocket.accept();
                handleClient(client);
            }
        } catch (IOException e) {
            // the window was closed, which closes serverSocket
        }
    }

    /** Reads one command per line and sends back one reply per line. */
    private void handleClient(Socket socket) {
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            String command;
            while ((command = input.readLine()) != null) {
                output.println(runOnFxThread(command));
            }
            socket.close();
        } catch (IOException e) {
            // the Controller disconnected
        }
    }

    /**
     * JavaFX only lets you change the screen from its own thread, and the
     * server is on a different thread. So we hand the command to the JavaFX
     * thread with Platform.runLater and wait (up to 2 seconds) for the answer.
     */
    private String runOnFxThread(String command) {
        CompletableFuture<String> reply = new CompletableFuture<>();
        Platform.runLater(() -> reply.complete(executeCommand(command)));
        try {
            return reply.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "ERROR GUI did not process command";
        }
    }

    /** Closes the server and the Controller's connection. */
    private void closeServer() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (client != null) client.close();
        } catch (IOException e) {
            // closing anyway
        }
    }

    /**
     * The twin's side of the API. Splits the command into words and has one
     * if for each command, which calls the right device. Always replies with
     * one line: OK ... for outputs, VALUE ... for inputs, or ERROR.
     */
    private String executeCommand(String command) {
        try {
            String[] parts = command.trim().toUpperCase().split("\\s+");

            // ----- outputs (the Controller changing something) -----
            // While the power is off these are ignored so the lights keep blinking red.

            // SET_TRAFFIC_LIGHT dir lane display color -> Traffic Lights
            if (parts.length == 5 && parts[0].equals("SET_TRAFFIC_LIGHT")) {
                Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane lane = Multiplexor.Lane.valueOf(parts[2]);
                Multiplexor.Display display = Multiplexor.Display.valueOf(parts[3]);
                Multiplexor.SignalColor color = Multiplexor.SignalColor.valueOf(parts[4]);

                if (!powerSensor.isOn()) return "OK NO_POWER";
                if (!trafficLights.setOutput(direction, lane, display, color)) {
                    return "ERROR unknown traffic light";
                }
                return "OK TRAFFIC_LIGHT_SET";
            }

            // SET_PED_LIGHT WALK/STOP -> Pedestrian lights
            if (parts.length == 2 && parts[0].equals("SET_PED_LIGHT")) {
                Multiplexor.PedStatus pedStatus = Multiplexor.PedStatus.valueOf(parts[1]);
                if (!powerSensor.isOn()) return "OK NO_POWER";
                pedestrian.setWalkLight(pedStatus == Multiplexor.PedStatus.WALK);
                return "OK PED_LIGHT " + pedStatus;
            }

            // ----- inputs (the Controller asking a question) -----

            // PED_REQUEST -> Pedestrian button
            if (parts.length == 1 && parts[0].equals("PED_REQUEST")) {
                return "VALUE " + pedestrian.isRequested();
            }

            // PED_CLEAR_REQUEST -> Pedestrian button
            if (parts.length == 1 && parts[0].equals("PED_CLEAR_REQUEST")) {
                return "VALUE " + pedestrian.clearRequest();
            }

            // EMERGENCY dir -> Emergency Vehicle Detector
            if (parts.length == 2 && parts[0].equals("EMERGENCY")) {
                Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[1]);
                return "VALUE " + emergencyVehicleDetector.detect(direction);
            }

            // CAR_DETECTION dir lane -> Induction Sensor
            if (parts.length == 3 && parts[0].equals("CAR_DETECTION")) {
                Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[1]);
                Multiplexor.Lane lane = Multiplexor.Lane.valueOf(parts[2]);
                return "VALUE " + inductionSensor.detect(direction, lane);
            }

            // the extra inputs that only our Controller uses

            // DAY_NIGHT -> Day/Night Timer
            if (parts.length == 1 && parts[0].equals("DAY_NIGHT")) {
                return "VALUE " + dayNightTimer.get();
            }

            // GREEN_INTERVAL -> Day/Night Timer
            if (parts.length == 1 && parts[0].equals("GREEN_INTERVAL")) {
                return "VALUE " + dayNightTimer.getIntervalSeconds();
            }

            // POWER -> Power Sensor
            if (parts.length == 1 && parts[0].equals("POWER")) {
                return "VALUE " + powerSensor.isOn();
            }

            return "ERROR invalid command";
        } catch (IllegalArgumentException e) {
            // valueOf() throws this if the direction, lane, color, etc. is spelled wrong
            return "ERROR invalid command value";
        }
    }
}
