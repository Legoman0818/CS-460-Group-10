import java.io.IOException;
import java.util.Arrays;

/**
 * The Controller. Main creates it and calls run().
 *
 * run() loops forever: it reads the input devices through the Multiplexor,
 * decides what mode the intersection should be in, and sets the traffic and
 * pedestrian lights. It only talks to the intersection through the
 * Multiplexor, so it would work with any twin that uses the same commands.
 */
public class Controller {

    private static final long TICK_MS = 100;                // how often we check the inputs
    private static final double ALL_RED_START_SECONDS = 2;
    private static final double WALK_SECONDS = 10;
    private static final double NIGHT_GREEN_SECONDS = 20;
    private static final double NIGHT_MIN_GREEN_SECONDS = 5; // shortest a night green can be

    // The 4 patterns DAY and NIGHT mode go through. Each lane is written as
    // "DIRECTION LANE" where the lane is L (left), C (straight), or R (right).
    private static final String[][] PATTERNS = {
        {"NORTH L", "SOUTH L"},                       // north/south left turns
        {"NORTH C", "NORTH R", "SOUTH C", "SOUTH R"}, // north/south straight + right
        {"EAST L", "WEST L"},                         // east/west left turns
        {"EAST C", "EAST R", "WEST C", "WEST R"},     // east/west straight + right
    };

    private final String host;
    private final int port;
    private Multiplexor multiplexor;

    private Mode mode = Mode.ALL_RED_START;
    private long phaseEndsAt;               // time (ms) the current phase is over
    private long greenStartedAt;            // time (ms) the current green started
    private int pattern = 0;                // which pattern we are on
    private boolean yellow = false;         // true during a pattern's yellow
    private Multiplexor.Direction emergencyApproach;

    public Controller(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Connects and runs the intersection until the connection is closed. */
    public void run() {
        try (Multiplexor mux = new Multiplexor(host, port)) {
            multiplexor = mux;
            enterAllRedStart();
            while (true) {
                step();
                Thread.sleep(TICK_MS);
            }
        } catch (IOException e) {
            // this happens when the twin window is closed
            System.out.println("Controller stopped: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One pass of the loop. Checked in order of importance:
     * power, then emergency vehicles, then everything else.
     */
    private void step() throws IOException {
        // Power failure. The power sensor already made everything red.
        if (!multiplexor.powerOn()) {
            mode = Mode.NO_POWER;
            return;
        }
        if (mode == Mode.NO_POWER) {
            enterAllRedStart(); // power just came back
            return;
        }

        // Emergency vehicle
        Multiplexor.Direction approach = detectEmergency();
        if (approach != null) {
            if (mode != Mode.EMERGENCY || approach != emergencyApproach) {
                enterEmergency(approach);
            }
            return;
        }
        if (mode == Mode.EMERGENCY) {
            enterNormal(); // the vehicle is gone
            return;
        }

        Mode dayOrNight = multiplexor.dayNightMode();
        boolean timeUp = System.currentTimeMillis() >= phaseEndsAt;

        switch (mode) {
            case ALL_RED_START -> {
                if (timeUp) enterNormal();
            }
            case DAY, NIGHT -> {
                // The timer can switch day/night at any time. The new green
                // length is used starting with the next green.
                mode = dayOrNight;
                if (timeUp) {
                    nextPhase();
                } else if (mode == Mode.NIGHT && carWaitingForNextPattern()) {
                    nextPhase(); // at night, don't make a waiting car sit through the whole green
                }
            }
            case PED_CROSSING -> {
                if (timeUp) {
                    // Clear the button only now, so if an emergency cuts the
                    // crossing short it still happens after.
                    multiplexor.pedClearRequest();
                    multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
                    enterNormal();
                }
            }
            default -> { }
        }
    }

    // ---------------- modes ----------------

    /** Startup: all red for a couple seconds, then start from pattern 0. */
    private void enterAllRedStart() throws IOException {
        mode = Mode.ALL_RED_START;
        pattern = 0;
        multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
        allRed();
        startTimer(ALL_RED_START_SECONDS);
    }

    /** Goes to DAY or NIGHT, whichever the timer says, and starts a green. */
    private void enterNormal() throws IOException {
        mode = multiplexor.dayNightMode();
        startGreen();
    }

    private void enterCrossing() throws IOException {
        mode = Mode.PED_CROSSING;
        allRed();
        multiplexor.setPedLight(Multiplexor.PedStatus.WALK);
        startTimer(WALK_SECONDS);
    }

    /** All 3 lanes of the emergency vehicle's direction go green. */
    private void enterEmergency(Multiplexor.Direction approach) throws IOException {
        mode = Mode.EMERGENCY;
        emergencyApproach = approach;
        multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
        showOnly(new String[] {approach + " L", approach + " C", approach + " R"},
                Multiplexor.SignalColor.GREEN);
    }

    // ---------------- day/night cycle ----------------

    /** green -> yellow -> (crossing if someone pressed the button) -> next green */
    private void nextPhase() throws IOException {
        if (!yellow) {
            yellow = true;
            for (String lane : PATTERNS[pattern]) {
                setLight(lane, Multiplexor.SignalColor.YELLOW);
            }
            startTimer(greenSeconds() / 5.0); // yellow is 1/5 of green
            return;
        }

        pattern = (pattern + 1) % PATTERNS.length;
        if (multiplexor.pedRequest()) {
            enterCrossing();
        } else {
            startGreen();
        }
    }

    private void startGreen() throws IOException {
        yellow = false;
        showOnly(PATTERNS[pattern], Multiplexor.SignalColor.GREEN);
        greenStartedAt = System.currentTimeMillis();
        startTimer(greenSeconds());
    }

    /** Day uses the timer's green interval, night always uses 20 seconds. */
    private double greenSeconds() throws IOException {
        if (mode == Mode.NIGHT) return NIGHT_GREEN_SECONDS;
        return multiplexor.greenInterval();
    }

    /**
     * Night only. True if the green has been on for at least the minimum
     * time and the induction sensor sees a car waiting in the next pattern.
     */
    private boolean carWaitingForNextPattern() throws IOException {
        if (yellow) return false;
        if (System.currentTimeMillis() - greenStartedAt < NIGHT_MIN_GREEN_SECONDS * 1000) {
            return false;
        }

        String[] nextPattern = PATTERNS[(pattern + 1) % PATTERNS.length];
        for (String lane : nextPattern) {
            String[] parts = lane.split(" ");
            Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[0]);
            Multiplexor.Lane l = Multiplexor.Lane.valueOf(parts[1]);
            if (multiplexor.carDetection(direction, l)) return true;
        }
        return false;
    }

    // ---------------- helpers ----------------

    /** Returns the direction an emergency vehicle is coming from, or null. */
    private Multiplexor.Direction detectEmergency() throws IOException {
        for (Multiplexor.Direction d : Multiplexor.Direction.values()) {
            if (multiplexor.emergency(d)) return d;
        }
        return null;
    }

    /**
     * Sets the given lanes to color and all the other lanes to red.
     * The reds are set first so two crossing directions are never green
     * at the same time.
     */
    private void showOnly(String[] lanes, Multiplexor.SignalColor color) throws IOException {
        for (Multiplexor.Direction d : Multiplexor.Direction.values()) {
            for (Multiplexor.Lane l : Multiplexor.Lane.values()) {
                String lane = d + " " + l;
                if (!Arrays.asList(lanes).contains(lane)) {
                    setLight(lane, Multiplexor.SignalColor.RED);
                }
            }
        }
        for (String lane : lanes) {
            setLight(lane, color);
        }
    }

    private void allRed() throws IOException {
        showOnly(new String[0], Multiplexor.SignalColor.RED);
    }

    /** Sets one light. lane is written like "NORTH L". */
    private void setLight(String lane, Multiplexor.SignalColor color) throws IOException {
        String[] parts = lane.split(" ");
        Multiplexor.Direction direction = Multiplexor.Direction.valueOf(parts[0]);
        Multiplexor.Lane l = Multiplexor.Lane.valueOf(parts[1]);

        // left and right lanes are arrows, the straight lane is a full circle
        Multiplexor.Display display = switch (l) {
            case L -> Multiplexor.Display.LEFT_ARROW;
            case R -> Multiplexor.Display.RIGHT_ARROW;
            case C -> Multiplexor.Display.FULL;
        };
        multiplexor.setTrafficLight(direction, l, display, color);
    }

    private void startTimer(double seconds) {
        phaseEndsAt = System.currentTimeMillis() + (long) (seconds * 1000);
    }
}
