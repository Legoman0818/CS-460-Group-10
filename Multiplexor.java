import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * The Multiplexor is how the Controller talks to the intersection.
 *
 * Each method turns into a one line text command that gets sent over a
 * socket to the digital twin (Crosswalk). Then it waits for the one line
 * reply. This way the Controller doesn't have to deal with any networking.
 */
public class Multiplexor implements AutoCloseable {

    // Using enums so we can't send a misspelled direction, color, etc.
    public enum Direction { NORTH, SOUTH, EAST, WEST }
    public enum Lane { L, R, C }
    public enum Display { FULL, RIGHT_ARROW, LEFT_ARROW, OFF }
    public enum SignalColor { RED, YELLOW, GREEN }
    public enum PedStatus { WALK, STOP }

    /** Makes the name used to look up a lane's light or car, like "NORTH_LEFT". */
    public static String laneKey(Direction direction, Lane lane) {
        String laneName = switch (lane) {
            case L -> "LEFT";
            case R -> "RIGHT";
            case C -> "STRAIGHT";
        };
        return direction + "_" + laneName;
    }

    private final Socket socket;
    private final BufferedReader input;   // replies from the twin
    private final PrintWriter output;     // commands to the twin

    public Multiplexor(String host, int port) throws IOException {
        socket = new Socket(host, port);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);
    }

    // ---------------- API agreed on with the other groups ----------------

    /** True if the pedestrian button has been pressed and not cleared yet. */
    public boolean pedRequest() throws IOException {
        return sendBoolean("PED_REQUEST");
    }

    /** Clears the button press. Returns true if there was a press to clear. */
    public boolean pedClearRequest() throws IOException {
        return sendBoolean("PED_CLEAR_REQUEST");
    }

    /** Sets one lane's traffic light. */
    public void setTrafficLight(Direction direction, Lane lane,
                                Display display, SignalColor color) throws IOException {
        send("SET_TRAFFIC_LIGHT " + direction + " " + lane + " " + display + " " + color);
    }

    /** Sets all the pedestrian lights to WALK or STOP. */
    public void setPedLight(PedStatus status) throws IOException {
        send("SET_PED_LIGHT " + status);
    }

    /** True if an emergency vehicle is coming from this direction. */
    public boolean emergency(Direction direction) throws IOException {
        return sendBoolean("EMERGENCY " + direction);
    }

    /** True if a car is waiting in this lane. */
    public boolean carDetection(Direction direction, Lane lane) throws IOException {
        return sendBoolean("CAR_DETECTION " + direction + " " + lane);
    }

    // ---------------- extra inputs (not in the agreed API) ----------------
    // Not public, so only our Controller uses them.

    /** DAY or NIGHT from the Day/Night Timer. */
    Mode dayNightMode() throws IOException {
        return Mode.valueOf(readValue(send("DAY_NIGHT")));
    }

    /** How long a green light lasts, in seconds. */
    double greenInterval() throws IOException {
        return Double.parseDouble(readValue(send("GREEN_INTERVAL")));
    }

    /** False if there is a power failure. */
    boolean powerOn() throws IOException {
        return sendBoolean("POWER");
    }

    // ---------------- sending and reading ----------------

    /** Sends one command and returns the one line reply. */
    private String send(String command) throws IOException {
        output.println(command);
        String response = input.readLine();
        if (response == null) throw new IOException("Digital Twin disconnected");
        if (response.startsWith("ERROR")) throw new IOException(response);
        return response;
    }

    private boolean sendBoolean(String command) throws IOException {
        String response = send(command);
        if (response.equalsIgnoreCase("VALUE TRUE")) return true;
        if (response.equalsIgnoreCase("VALUE FALSE")) return false;
        throw new IOException("Expected VALUE TRUE/FALSE but got: " + response);
    }

    /** Takes "VALUE something" and returns "something". */
    private String readValue(String response) throws IOException {
        if (!response.startsWith("VALUE ")) {
            throw new IOException("Expected VALUE but got: " + response);
        }
        return response.substring("VALUE ".length()).trim();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
