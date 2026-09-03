import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Public API used by Main to exercise devices in the Digital Twin.
 *
 * Every method converts a simple Java call into a text command sent through a
 * socket. Main never changes Crosswalk directly.
 */
public class Multiplexor implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader input;
    private final PrintWriter output;

    public Multiplexor(String host, int port) throws IOException {
        socket = new Socket(host, port);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);
    }

    public String ping() throws IOException {
        return send("PING");
    }

    /**
     * Signal names: NORTH_LEFT, NORTH_STRAIGHT, NORTH_RIGHT, and the same
     * LEFT/STRAIGHT/RIGHT names for SOUTH, EAST, and WEST.
     * Colors: GREEN, YELLOW, or RED.
     */
    public String setSignal(String signalName, String color) throws IOException {
        return send("SET_SIGNAL " + signalName + " " + color);
    }

    /** Pedestrian zone names: NW, NE, SW, or SE. */
    public String setPedestrian(String zoneName, boolean active) throws IOException {
        return send("PEDESTRIAN " + zoneName + " " + (active ? "ON" : "OFF"));
    }

    /** Simulates an emergency message arriving through the antenna. */
    public String activateEmergencyMode() throws IOException {
        return send("ANTENNA ACTIVATE");
    }

    private String send(String command) throws IOException {
        output.println(command);
        String response = input.readLine();
        if (response == null) throw new IOException("Digital Twin disconnected");
        return response;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}