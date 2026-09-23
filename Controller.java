import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Main decision-making API for the Traffic Control System.
 *
 * Simulation controls report events to this class. The Controller then sends
 * the appropriate high-level command through Multiplexor. It contains the
 * traffic-system rules; it does not draw the intersection or parse sockets.
 */
public class Controller implements AutoCloseable {

    // A WALK request automatically ends after this many seconds.
    private static final int CROSSING_SECONDS = 10;

    // All communication with the digital twin passes through this object.
    private final Multiplexor multiplexor;

    // This single background thread is used only for the pedestrian countdown.
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "pedestrian-timer");
                thread.setDaemon(true);
                return thread;
            });
    private ScheduledFuture<?> crossingTimer; // current countdown, if any
    private Multiplexor.Mode mode = Multiplexor.Mode.DAY; // remembered UI mode
    private boolean powerAvailable = true;    // false after Power Off
    private boolean crossingActive = false;   // blocks unsafe mode/light changes

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
        // A reset must also stop any old countdown from changing the new state.
        cancelCrossingTimer();
        crossingActive = false;
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
        requireNoActiveCrossing();
        String response = multiplexor.setMode(newMode);
        mode = newMode;
        return response;
    }

    /**
     * Starts a timed pedestrian crossing. All signals are made safe by the
     * digital twin when it receives WALK, and STOP is sent ten seconds later.
     */
    public synchronized String pedestrianRequest() throws IOException {
        requirePower();
        cancelCrossingTimer();
        multiplexor.setPedLight(Multiplexor.PedStatus.WALK);
        crossingActive = true;
        crossingTimer = timer.schedule(
                this::finishPedestrianCrossing,
                CROSSING_SECONDS, TimeUnit.SECONDS);
        return "OK PEDESTRIAN_REQUEST";
    }

    /** Asks the sensor API whether a car is waiting in a particular lane. */
    public synchronized boolean vehicleDetected(Multiplexor.Direction direction,
                                                 Multiplexor.Lane lane)
            throws IOException {
        requirePower();
        return multiplexor.carDetection(direction, lane);
    }

    /**
     * Gives an emergency vehicle priority from its entry side to its exit side.
     * Any active pedestrian countdown is cancelled before the route begins.
     */
    public synchronized String emergencyDetected(Multiplexor.Direction approach,
                                                  Multiplexor.Direction destination)
            throws IOException {
        requirePower();
        if (approach == destination) {
            throw new IllegalArgumentException(
                    "Approach and destination must be different");
        }
        cancelCrossingTimer();
        crossingActive = false;
        return multiplexor.simulateEmergencyRoute(approach, destination);
    }

    /** Places the simulated intersection in its all-red failsafe state. */
    public synchronized String powerFailure() throws IOException {
        cancelCrossingTimer();
        crossingActive = false;
        powerAvailable = false;
        return multiplexor.powerFailure();
    }

    /** Allows a user click to cycle one programmable traffic signal. */
    public synchronized String manualSignalChange(String signalName)
            throws IOException {
        requirePower();
        requireNoActiveCrossing();
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

    /** Timer callback that changes the pedestrian lights back to STOP. */
    private synchronized void finishPedestrianCrossing() {
        if (!powerAvailable) return;
        try {
            multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
            crossingActive = false;
        } catch (IOException e) {
            System.err.println("Could not end pedestrian crossing: "
                    + e.getMessage());
        }
    }

    /** Cancels and forgets the current countdown, if one exists. */
    private void cancelCrossingTimer() {
        if (crossingTimer != null) {
            crossingTimer.cancel(false);
            crossingTimer = null;
        }
    }

    /** Prevents manual changes from interrupting a protected crossing period. */
    private void requireNoActiveCrossing() {
        if (crossingActive) {
            throw new IllegalStateException(
                    "Wait for the pedestrian timer to finish.");
        }
    }

    @Override
    public void close() throws IOException {
        // Stop background work before closing the network connection.
        cancelCrossingTimer();
        timer.shutdownNow();
        multiplexor.close();
    }
}
