import java.io.IOException;

/**
 * Main decision-making API for the Traffic Control System.
 *
 * Simulation controls report events to this class. The Controller then sends
 * the appropriate high-level command through Multiplexor. It contains the
 * traffic-system rules; it does not draw the intersection or parse sockets.
 *
 * The digital twin (Crosswalk) now owns pedestrian-crossing timing itself,
 * since it has to line the WALK phase up with its own signal cycle rather
 * than granting it the instant it is requested. Controller therefore just
 * forwards the request and does not run its own countdown.
 */
public class Controller implements AutoCloseable {

    // All communication with the digital twin passes through this object.
    private final Multiplexor multiplexor;

    private Multiplexor.Mode mode = Multiplexor.Mode.DAY; // remembered UI mode
    private boolean powerAvailable = true;    // false after Power Off

    /** Creates the Multiplexor connection used for every controller action. */
    public Controller(String host, int port) throws IOException {
        multiplexor = new Multiplexor(host, port);
    }

    /** Starts normal traffic operation after the window has been created. */
    public synchronized String start() throws IOException {
        powerAvailable = true;
        return multiplexor.start();
    }

    /** Returns all controller state and the digital twin to their defaults. */
    public synchronized String reset() throws IOException {
        mode = Multiplexor.Mode.DAY;
        powerAvailable = true;
        String response = multiplexor.reset();
        multiplexor.start();
        return response;
    }

    /** Switches between day and night operation when it is safe to do so. */
    public synchronized String setMode(Multiplexor.Mode newMode)
            throws IOException {
        requirePower();
        String response = multiplexor.setMode(newMode);
        mode = newMode;
        return response;
    }

    /**
     * Requests a pedestrian crossing. The digital twin decides when it is
     * safe to actually show WALK (at the next gap between signal patterns)
     * and how long it lasts, so this call does not block or start a timer.
     */
    public synchronized String pedestrianRequest() throws IOException {
        requirePower();
        multiplexor.setPedLight(Multiplexor.PedStatus.WALK);
        return "OK PEDESTRIAN_REQUEST";
    }

    /** Asks the sensor API whether a car is waiting in a particular lane. */
    public synchronized boolean vehicleDetected(Multiplexor.Direction direction,
                                                 Multiplexor.Lane lane)
            throws IOException {
        requirePower();
        return multiplexor.carDetection(direction, lane);
    }

    /** Gives an emergency vehicle priority from its entry side to its exit side. */
    public synchronized String emergencyDetected(Multiplexor.Direction approach,
                                                  Multiplexor.Direction destination)
            throws IOException {
        requirePower();
        if (approach == destination) {
            throw new IllegalArgumentException(
                    "Approach and destination must be different");
        }
        return multiplexor.simulateEmergencyRoute(approach, destination);
    }

    /** Places the simulated intersection in its all-red failsafe state. */
    public synchronized String powerFailure() throws IOException {
        powerAvailable = false;
        return multiplexor.powerFailure();
    }

    /** Allows a user click to cycle one programmable traffic signal. */
    public synchronized String manualSignalChange(String signalName)
            throws IOException {
        requirePower();
        return multiplexor.cycleSignal(signalName);
    }

    /** Returns the mode remembered by the controller for the control panel. */
    public synchronized Multiplexor.Mode getMode() {
        return mode;
    }

    /** Rejects actions that should not occur while simulated power is off. */
    private void requirePower() {
        if (!powerAvailable) {
            throw new IllegalStateException("Power is off. Reset the system first.");
        }
    }

    @Override
    public void close() throws IOException {
        multiplexor.close();
    }
}
