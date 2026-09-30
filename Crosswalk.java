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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The digital twin. This is the fake intersection that the Controller runs.
 *
 * It doesn't make any traffic decisions. It:
 *  - draws the intersection and holds all the devices
 *  - runs a server that answers the Multiplexor's commands
 *  - has a side panel to make things happen (press the button, send an
 *    emergency vehicle, power failure, etc.)
 *  - moves the cars
 *
 * Main starts this window and then runs the Controller.
 */
public class Crosswalk extends Application {

    static final int PORT = 5000;

    // Lets Main wait until the server is running before the Controller connects.
    private static final CountDownLatch READY = new CountDownLatch(1);

    // everything on the intersection is drawn on this
    private final Pane root = new Pane();

    // devices
    private TrafficLights trafficLights;
    private Pedestrian pedestrian;
    private InductionSensor inductionSensor;
    private EmergencyVehicleDetector emergencyVehicleDetector;
    private final DayNightTimer dayNightTimer = new DayNightTimer();
    private final PowerSensor powerSensor = new PowerSensor();

    // one car per lane, keys look like "NORTH_LEFT"
    private final Map<String, Car> carsByLane = new HashMap<>();
    private EmergencyVehicle activeEmergencyVehicle;

    // server
    private ServerSocket serverSocket;
    private volatile boolean serverRunning;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    // panel things that get updated from more than one place
    private Label status;
    private Label modeValue;
    private Button dayNight;

    /** Main calls this to wait until the twin is ready. */
    static void awaitReady() throws InterruptedException {
        if (!READY.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Digital twin did not start");
        }
    }

    @Override
    public void start(Stage stage) {
        root.setPrefSize(Roads.W, Roads.H);
        new Roads(root);

        emergencyVehicleDetector = new EmergencyVehicleDetector(root);
        trafficLights = new TrafficLights(root, this::manualSignalChange);

        // cars go on top of the road but under the lights
        addCars();
        inductionSensor = new InductionSensor(carsByLane);

        pedestrian = new Pedestrian(root, Roads.BG,
                () -> showStatus("Pedestrian crossing requested"));
        trafficLights.bringAllToFront();

        // Only the intersection scales when the window is resized,
        // the panel on the right stays the same size.
        Group content = new Group(root);
        Pane frame = new Pane(content);
        frame.setStyle("-fx-background-color: #0d0d0d;");
        content.scaleXProperty().bind(
                javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> Math.min(frame.getWidth() / Roads.W, frame.getHeight() / Roads.H),
                        frame.widthProperty(), frame.heightProperty()));
        content.scaleYProperty().bind(content.scaleXProperty());

        BorderPane window = new BorderPane();
        window.setCenter(frame);
        window.setRight(createPanel());

        stage.setTitle("Crosswalk - Traffic Control System (Group 10)");
        stage.setScene(new Scene(window, Roads.W + 290, Roads.H, Roads.BG));
        stage.show();

        startServer();
        READY.countDown();
    }

    /** Called when the window closes. */
    @Override
    public void stop() {
        closeServer(); // this also makes Controller.run() end
    }

    // ---------------- cars ----------------

    /** Makes one car for each lane and starts moving them. */
    private void addCars() {
        List<Car> cars = new ArrayList<>();
        for (Map.Entry<String, double[][]> entry : Roads.ROUTES.entrySet()) {
            String lane = entry.getKey();
            String direction = lane.substring(0, lane.indexOf('_'));
            Car car = new Car(entry.getValue(), carColor(direction),
                    () -> trafficLights.isGreen(lane));
            carsByLane.put(lane, car);
            cars.add(car);
        }
        root.getChildren().addAll(cars);

        // runs every frame
        new AnimationTimer() {
            private long previousTime;

            @Override
            public void handle(long now) {
                if (previousTime == 0) {
                    previousTime = now;
                    return;
                }
                double seconds = (now - previousTime) / 1_000_000_000.0; // ns to seconds
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

    // ---------------- side panel ----------------

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

        // system buttons
        Button reset = button("Reset System", "#334155");
        reset.setOnAction(e -> resetSystem());
        Button powerOff = button("Power Off", "#7f1d1d");
        powerOff.setOnAction(e -> powerFailure());
        Button requestCrossing = button("Request Crossing", "#1e3a5f");
        requestCrossing.setOnAction(e -> requestCrossing());
        VBox systemCard = card(sectionLabel("SYSTEM"), reset, powerOff, requestCrossing);

        // emergency vehicle
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

        // day/night
        modeValue = new Label();
        modeValue.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");
        dayNight = button("", "#334155");
        dayNight.setOnAction(e -> toggleDayNight());
        updateModeCard();
        VBox modeCard = card(sectionLabel("DAY/NIGHT TIMER"), modeValue, dayNight);

        // green light time
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

    // ---------------- panel actions ----------------
    // These only change the devices. They don't call the Controller,
    // it sees the change the next time it checks the devices.

    private void showStatus(String message) {
        if (status != null) status.setText("●  " + message);
    }

    private void resetSystem() {
        removeEmergencyVehicle();
        pedestrian.clearRequest();
        dayNightTimer.set(Mode.DAY);
        powerSensor.restore(trafficLights);
        updateModeCard();
        showStatus("System reset to day mode");
    }

    private void powerFailure() {
        removeEmergencyVehicle();
        powerSensor.trip(trafficLights);
        pedestrian.setWalkLight(false);
        showStatus("Power failure: failsafe active");
    }

    private void requestCrossing() {
        if (!powerSensor.isOn()) {
            showStatus("Power is off. Reset the system first.");
            return;
        }
        pedestrian.press();
        showStatus("Pedestrian crossing requested; will WALK at the next signal change");
    }

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

        // drive the route of the lane that turns the right way
        double[][] route = Roads.ROUTES.get(Multiplexor.laneKey(from, Roads.laneFor(from, to)));
        activeEmergencyVehicle = new EmergencyVehicle(route, this::removeEmergencyVehicle);
        root.getChildren().add(activeEmergencyVehicle);
        activeEmergencyVehicle.play();
        showStatus("Emergency route: " + from + " to " + to);
    }

    /** Removes the emergency vehicle (if there is one) and clears the detector. */
    private void removeEmergencyVehicle() {
        if (activeEmergencyVehicle != null) {
            activeEmergencyVehicle.stop();
            root.getChildren().remove(activeEmergencyVehicle);
            activeEmergencyVehicle = null;
        }
        emergencyVehicleDetector.clearActiveApproach();
    }

    private void toggleDayNight() {
        dayNightTimer.set(dayNightTimer.isDay() ? Mode.NIGHT : Mode.DAY);
        updateModeCard();
        showStatus("Day/Night Timer set to " + dayNightTimer.get());
    }

    private void updateModeCard() {
        modeValue.setText(dayNightTimer.get() + " MODE");
        dayNight.setText(dayNightTimer.isDay() ? "Switch to Night" : "Switch to Day");
    }

    /** Clicking a light changes its color. The Controller changes it back at its next change. */
    private void manualSignalChange(String name) {
        if (!powerSensor.isOn()) return;
        trafficLights.cycle(name);
        showStatus("Manual signal change: " + name);
    }

    // ---------------- server ----------------

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            serverRunning = true;
        } catch (IOException e) {
            throw new IllegalStateException("Could not open port " + PORT, e);
        }

        // daemon threads so they don't keep the program running after it closes
        Thread serverThread = new Thread(this::acceptClients, "twin-server");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("Digital Twin listening on port " + PORT);
    }

    /** Waits for connections. Each client gets its own thread. */
    private void acceptClients() {
        while (serverRunning) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
                Thread clientThread = new Thread(() -> handleClient(client), "twin-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException e) {
                if (serverRunning) System.err.println("Socket accept error: " + e.getMessage());
            }
        }
    }

    /** Reads one command per line and sends back one reply per line. */
    private void handleClient(Socket client) {
        try (Socket socket = client;
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {
            String command;
            while ((command = input.readLine()) != null) {
                output.println(runOnFxThread(command));
            }
        } catch (IOException e) {
            if (serverRunning) System.err.println("Client connection error: " + e.getMessage());
        } finally {
            clients.remove(client);
        }
    }

    /**
     * JavaFX only lets you change the screen from its own thread, so the
     * command is handed to that thread with Platform.runLater and we wait
     * for the answer.
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

    /** Closes the server and all connections. */
    private void closeServer() {
        serverRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            for (Socket client : clients) client.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    /**
     * Handles one command from the Multiplexor. Always returns a reply:
     * OK, VALUE ..., or ERROR.
     */
    private String executeCommand(String command) {
        try {
            String[] parts = command.trim().toUpperCase().split("\\s+");

            // ----- outputs -----
            // When the power is off the lights stay red, so these do nothing.

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

            if (parts.length == 2 && parts[0].equals("SET_PED_LIGHT")) {
                Multiplexor.PedStatus pedStatus = Multiplexor.PedStatus.valueOf(parts[1]);
                if (!powerSensor.isOn()) return "OK NO_POWER";
                pedestrian.setWalkLight(pedStatus == Multiplexor.PedStatus.WALK);
                return "OK PED_LIGHT " + pedStatus;
            }

            // ----- inputs -----

            if (parts.length == 1 && parts[0].equals("PED_REQUEST")) {
                return "VALUE " + pedestrian.isRequested();
            }

            if (parts.length == 1 && parts[0].equals("PED_CLEAR_REQUEST")) {
                return "VALUE " + pedestrian.clearRequest();
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

            if (parts.length == 1 && parts[0].equals("DAY_NIGHT")) {
                return "VALUE " + dayNightTimer.get();
            }

            if (parts.length == 1 && parts[0].equals("GREEN_INTERVAL")) {
                return "VALUE " + dayNightTimer.getIntervalSeconds();
            }

            if (parts.length == 1 && parts[0].equals("POWER")) {
                return "VALUE " + powerSensor.isOn();
            }

            return "ERROR invalid command";
        } catch (IllegalArgumentException e) {
            // valueOf() throws this for a bad direction, lane, color, etc.
            return "ERROR invalid command value";
        }
    }
}
