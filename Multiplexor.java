import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Multiplexor - how the Controller talks to the intersection.
 * Design Diagram: not on it as per instructions from the professor
 *
 * API: this class IS the Controller's side of the API. Every method below
 * turns into one line of text that gets sent over a socket to the twin
 * (Crosswalk), and then we wait for the one line reply. That way the
 * Controller never has to deal with any of the networking.
 */
public class Multiplexor implements AutoCloseable {

    //  Enums 
    // The allowed values. Using enums means we can't send a misspelled
    // direction or color by accident.
    public enum Direction { NORTH, SOUTH, EAST, WEST }
    public enum Lane { L, R, C }  // L = left turn, R = right turn, C = straight
    public enum Display { FULL, RIGHT_ARROW, LEFT_ARROW, OFF }
    public enum SignalColor { RED, YELLOW, GREEN }
    public enum PedStatus { WALK, STOP }

    /** Makes the name the twin uses to look up a light or a car. NORTH + L gives "NORTH_LEFT". */
    public static String laneKey(Direction direction, Lane lane) {
        String laneName = switch (lane) {
            case L -> "LEFT";
            case R -> "RIGHT";
            case C -> "STRAIGHT";
        };
        return direction + "_" + laneName;
    }

    private final Socket socket;
    private final BufferedReader input;   // replies coming back from the twin
    private final PrintWriter output;     // commands going to the twin

    //  Connecting 

    public Multiplexor(String host, int port) throws IOException, InterruptedException {
        socket = connect(host, port);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * The socket connection test
     */
    private static Socket connect(String host, int port) throws IOException, InterruptedException {
        for (int tries = 1; tries < 300; tries++) {
            try {
                return new Socket(host, port);
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        return new Socket(host, port); // last try, if this fails the error goes to the caller
    }

    //  API methods (agreed on with the other groups) 
    // These are public so another group's Controller can use them the same way.

    /** PED_REQUEST: true if the button was pressed and not cleared yet. */
    public boolean pedRequest() throws IOException {
        return sendBoolean("PED_REQUEST");
    }

    /** PED_CLEAR_REQUEST: clears the button press. True if there was one to clear. */
    public boolean pedClearRequest() throws IOException {
        return sendBoolean("PED_CLEAR_REQUEST");
    }

    /** SET_TRAFFIC_LIGHT: sets one lane's traffic light. */
    public void setTrafficLight(Direction direction, Lane lane,
                                Display display, SignalColor color) throws IOException {
        send("SET_TRAFFIC_LIGHT " + direction + " " + lane + " " + display + " " + color);
    }

    /** SET_PED_LIGHT: sets all the walk lights to WALK or STOP. */
    public void setPedLight(PedStatus status) throws IOException {
        send("SET_PED_LIGHT " + status);
    }

    /** EMERGENCY: true if an emergency vehicle is coming from this direction. */
    public boolean emergency(Direction direction) throws IOException {
        return sendBoolean("EMERGENCY " + direction);
    }

    /** CAR_DETECTION: true if a car is waiting at the stop line in this lane. */
    public boolean carDetection(Direction direction, Lane lane) throws IOException {
        return sendBoolean("CAR_DETECTION " + direction + " " + lane);
    }

    //  Extra inputs (not in the agreed API) 
    // Not public, because they aren't in the agreed API. Only our Controller
    // uses them, for day/night and power failure.
    // Once again, the professor told us to leave it out of our documentation.

    /** DAY_NIGHT: DAY or NIGHT from the Day/Night Timer. */
    Mode dayNightMode() throws IOException {
        return Mode.valueOf(readValue(send("DAY_NIGHT")));
    }

    /** GREEN_INTERVAL: how long a day green lasts, in seconds. */
    double greenInterval() throws IOException {
        return Double.parseDouble(readValue(send("GREEN_INTERVAL")));
    }

    /** POWER: false if there's a power failure. */
    boolean powerOn() throws IOException {
        return sendBoolean("POWER");
    }

    //  Sending and reading 

    /**
     * Sends one command and returns the one line reply.
     * Throws an error if the reply starts with ERROR or the twin is gone.
     */
    private String send(String command) throws IOException {
        output.println(command);
        String response = input.readLine();
        if (response == null) throw new IOException("Digital Twin disconnected");
        if (response.startsWith("ERROR")) throw new IOException(response);
        return response;
    }

    /** For the true/false questions. "VALUE TRUE" gives true. */
    private boolean sendBoolean(String command) throws IOException {
        String response = send(command);
        if (response.equalsIgnoreCase("VALUE TRUE")) return true;
        if (response.equalsIgnoreCase("VALUE FALSE")) return false;
        throw new IOException("Expected VALUE TRUE/FALSE but got: " + response);
    }

    /** Takes "VALUE something" and gives back "something", like "VALUE DAY" gives "DAY". */
    private String readValue(String response) throws IOException {
        if (!response.startsWith("VALUE ")) {
            throw new IOException("Expected VALUE but got: " + response);
        }
        return response.substring("VALUE ".length()).trim();
    }

    /** Closes the socket. The try x in Controller.run() calls this. */
    @Override
    public void close() throws IOException {
        socket.close();
    }
}
