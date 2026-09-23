import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Communication API used by Controller.
 *
 * Controller works with normal Java methods such as setPedLight(). This class
 * translates each method call into a short text command, sends the command to
 * DigitalTwinServer, and returns the server's response. Keeping that socket
 * work here prevents Controller from depending on networking details.
 */
public class Multiplexor implements AutoCloseable {

    // These enums define every value that the public device API accepts.
    // Enums prevent misspelled direction, lane, color, and status strings.
    public enum Mode { DAY, NIGHT }
    public enum Direction { NORTH, SOUTH, EAST, WEST }
    public enum Lane { L, R, C }
    public enum Display { FULL, RIGHT_ARROW, LEFT_ARROW, OFF }
    public enum SignalColor { RED, YELLOW, GREEN }
    public enum PedStatus { WALK, STOP }

    private final Socket socket;          // connection to the digital twin
    private final BufferedReader input;   // reads one response line at a time
    private final PrintWriter output;     // sends one command line at a time

    /** Connects to the DigitalTwinServer at the supplied host and port. */
    public Multiplexor(String host, int port) throws IOException {
        socket = new Socket(host, port);
        input = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);
    }

    /* ---------------- AGREED DEVICE API ----------------
     * These five public methods represent the logical intersection devices.
     * They are the methods other parts of the design are allowed to call.
     */

    /** Returns whether the simulated pedestrian button is currently active. */
    public boolean pedRequest() throws IOException {
        return sendBoolean("PED_REQUEST");
    }

    /** Sets the display and color of one lane's programmable LED signal. */
    public void setTrafficLight(Direction direction, Lane lane,
                                Display display, SignalColor color)
            throws IOException {
        send("SET_TRAFFIC_LIGHT " + direction + " " + lane + " "
                + display + " " + color);
    }

    /** Changes all simulated pedestrian signals to WALK or STOP. */
    public void setPedLight(PedStatus status) throws IOException {
        send("SET_PED_LIGHT " + status);
    }

    /** Returns whether an emergency vehicle was detected from a direction. */
    public boolean emergency(Direction direction) throws IOException {
        return sendBoolean("EMERGENCY " + direction);
    }

    /** Returns whether a car is detected in one approach lane. */
    public boolean carDetection(Direction direction, Lane lane)
            throws IOException {
        return sendBoolean("CAR_DETECTION " + direction + " " + lane);
    }

    /* ---------------- SIMULATION-ONLY COMMANDS ----------------
     * These methods support the demonstration window. They have no public
     * modifier, so they remain available to Controller in this package without
     * becoming part of the agreed external-device API.
     */

    String start() throws IOException {
        return send("START");
    }

    String reset() throws IOException {
        return send("RESET");
    }

    String setMode(Mode mode) throws IOException {
        return send("SET_MODE " + mode);
    }

    String cycleSignal(String signalName) throws IOException {
        return send("CYCLE_SIGNAL " + signalName);
    }

    String simulateEmergencyRoute(Direction approach, Direction destination)
            throws IOException {
        return send("EMERGENCY_DETECTED " + approach + " " + destination);
    }

    String powerFailure() throws IOException {
        return send("POWER_FAILURE");
    }

    /** Sends a command that expects VALUE TRUE or VALUE FALSE in response. */
    private boolean sendBoolean(String command) throws IOException {
        return readBoolean(send(command));
    }

    /**
     * Sends exactly one command and waits for exactly one response.
     * Server-side ERROR responses become exceptions so Controller can handle
     * failures in the same way as socket errors.
     */
    private String send(String command) throws IOException {
        output.println(command);
        String response = input.readLine();
        if (response == null) throw new IOException("Digital Twin disconnected");
        if (response.startsWith("ERROR")) throw new IOException(response);
        return response;
    }

    /** Converts the digital twin's text response into a Java boolean. */
    private boolean readBoolean(String response) throws IOException {
        if (response.equalsIgnoreCase("VALUE TRUE")) return true;
        if (response.equalsIgnoreCase("VALUE FALSE")) return false;
        throw new IOException("Expected boolean response, received: " + response);
    }

    @Override
    public void close() throws IOException {
        // Closing the socket also closes its input and output streams.
        socket.close();
    }
}
