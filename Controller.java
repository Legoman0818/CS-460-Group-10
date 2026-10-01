import java.io.IOException;
import java.util.Arrays;

/**
 * Controller - makes every decision for the intersection. (Walkthrough 3.2)
 *
 * Design Diagram: this is the Controller box. The Mode Controller box and the
 * Day, Night, Emergency and Ped_Crossing boxes are methods in here, not their
 * own classes.
 *
 * API: we only talk to the intersection through the multiplexor field, so
 * every command in the API gets called from somewhere in this class. Because
 * of that it should work with any other group's twin that uses the same API.
 */
public class Controller {

    // ---------------- Settings ----------------

    private static final long TICK_MS = 100;                 // how often step() runs
    private static final double ALL_RED_START_SECONDS = 2;  // all red at startup
    private static final double WALK_SECONDS = 10;          // how long WALK stays on
    private static final double NIGHT_GREEN_SECONDS = 20;   // night greens are always 20s
    private static final double NIGHT_MIN_GREEN_SECONDS = 5; // a night green has to last at least this long

    // The lanes that are green together. DAY and NIGHT go through these in
    // order. Each lane is "DIRECTION LANE", L = left turn, C = straight, R = right turn.
    private static final String[][] PATTERNS = {
        {"NORTH L", "SOUTH L"},                       // 0: north/south left turns
        {"NORTH C", "NORTH R", "SOUTH C", "SOUTH R"}, // 1: north/south straight and right
        {"EAST L", "WEST L"},                         // 2: east/west left turns
        {"EAST C", "EAST R", "WEST C", "WEST R"},     // 3: east/west straight and right
    };

    private final String host;
    private final int port;
    private Multiplexor multiplexor;

    private Mode mode = Mode.ALL_RED_START; // the current mode (see Mode.java)
    private long phaseEndsAt;               // time (ms) the current light/phase is over
    private long greenStartedAt;            // time (ms) the current green started
    private int pattern = 0;                // which of the PATTERNS we are on
    private boolean yellow = false;         // true while the pattern is yellow
    private Multiplexor.Direction emergencyApproach; // where the emergency vehicle is coming from

    public Controller(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ---------------- run(): the main loop ----------------

    /**
     * Connects, goes all red, then loops forever: step(), wait 100 ms, repeat.
     * When the window is closed the connection closes, which throws an
     * IOException and ends the loop.
     */
    public void run() {
        try (Multiplexor mux = new Multiplexor(host, port)) {
            multiplexor = mux;
            enterAllRedStart();
            while (true) {
                step();
                Thread.sleep(TICK_MS);
            }
        } catch (IOException e) {
            // this is what happens when the twin window gets closed
            System.out.println("Controller stopped: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Controller stopped: interrupted");
        }
    }

    // ---------------- step(): the Mode Controller ----------------

    /**
     * One pass of the loop. This is the Mode Controller box on the diagram.
     * Things are checked in order of importance and the first one that
     * applies wins:
     *   1. power
     *   2. emergency vehicle
     *   3. everything else (the switch on the current mode)
     */
    private void step() throws IOException {
        // 1. Power. If it's off, the power sensor already made the lights
        //    blink red by itself, so all we do is remember we're in NO_POWER.
        if (!multiplexor.powerOn()) {
            mode = Mode.NO_POWER;
            return;
        }
        if (mode == Mode.NO_POWER) {
            enterAllRedStart(); // power just came back, start over from all red
            return;
        }

        // 2. Emergency vehicle (the Emergency box). Asks EMERGENCY for all 4 directions.
        Multiplexor.Direction approach = detectEmergency();
        if (approach != null) {
            // only change the lights if it's new or coming from a different direction
            if (mode != Mode.EMERGENCY || approach != emergencyApproach) {
                enterEmergency(approach);
            }
            return;
        }
        if (mode == Mode.EMERGENCY) {
            enterNormal(); // the vehicle is gone, go back to the normal cycle
            return;
        }

        // 3. Everything else, depending on the mode we're in
        Mode dayOrNight = multiplexor.dayNightMode();
        boolean timeUp = System.currentTimeMillis() >= phaseEndsAt;

        switch (mode) {
            case ALL_RED_START -> {
                if (timeUp) enterNormal(); // the 2 seconds are up, start the cycle
            }
            case DAY, NIGHT -> {
                // The Day and Night boxes. The timer can switch day/night
                // any time, the new green length starts at the next green.
                mode = dayOrNight;
                if (timeUp) {
                    nextPhase();
                } else if (mode == Mode.NIGHT && carWaitingForNextPattern()) {
                    nextPhase(); // at night, don't make a waiting car sit through the whole green
                }
            }
            case PED_CROSSING -> {
                // The Ped_Crossing box. When WALK is over, go back to the cycle.
                if (timeUp) {
                    // We only clear the button here at the end. That way if an
                    // emergency cuts the crossing short, it still happens after.
                    multiplexor.pedClearRequest();
                    multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
                    enterNormal();
                }
            }
            default -> { }
        }
    }

    // ---------------- Entering a mode ----------------

    /** Startup (and after the power comes back): all red for 2 seconds, start from pattern 0. */
    private void enterAllRedStart() throws IOException {
        mode = Mode.ALL_RED_START;
        pattern = 0;
        multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
        allRed();
        startTimer(ALL_RED_START_SECONDS);
    }

    /** Asks the timer if it's DAY or NIGHT, then starts a green. */
    private void enterNormal() throws IOException {
        mode = multiplexor.dayNightMode();
        startGreen();
    }

    /** Ped_Crossing: every traffic light red, walk lights on WALK for 10 seconds. */
    private void enterCrossing() throws IOException {
        mode = Mode.PED_CROSSING;
        allRed();
        multiplexor.setPedLight(Multiplexor.PedStatus.WALK);
        startTimer(WALK_SECONDS);
    }

    /** Emergency: walk lights STOP, all 3 lanes of the vehicle's direction green, the rest red. */
    private void enterEmergency(Multiplexor.Direction approach) throws IOException {
        mode = Mode.EMERGENCY;
        emergencyApproach = approach;
        multiplexor.setPedLight(Multiplexor.PedStatus.STOP);
        showOnly(new String[] {approach + " L", approach + " C", approach + " R"},
                Multiplexor.SignalColor.GREEN);
    }

    // ---------------- The day/night cycle ----------------

    /**
     * Moves the cycle along: green -> yellow -> next pattern's green.
     * If someone pressed the button, the crossing goes in before the next green.
     */
    private void nextPhase() throws IOException {
        if (!yellow) {
            // green is over, turn this pattern yellow
            yellow = true;
            for (String lane : PATTERNS[pattern]) {
                setLight(lane, Multiplexor.SignalColor.YELLOW);
            }
            startTimer(greenSeconds() / 5.0); // yellow is 1/5 of the green time
            return;
        }

        // yellow is over, move on to the next pattern
        pattern = (pattern + 1) % PATTERNS.length;
        if (multiplexor.pedRequest()) {
            enterCrossing();
        } else {
            startGreen();
        }
    }

    /** Makes the current pattern green and everything else red, then starts the timer. */
    private void startGreen() throws IOException {
        yellow = false;
        showOnly(PATTERNS[pattern], Multiplexor.SignalColor.GREEN);
        greenStartedAt = System.currentTimeMillis();
        startTimer(greenSeconds());
    }

    /** NIGHT is always 20 seconds. DAY uses GREEN_INTERVAL from the timer (set in the panel). */
    private double greenSeconds() throws IOException {
        if (mode == Mode.NIGHT) return NIGHT_GREEN_SECONDS;
        return multiplexor.greenInterval();
    }

    /**
     * Night only (uses the Induction Sensor). After the green has been on for
     * at least 5 seconds, asks CAR_DETECTION for every lane in the next
     * pattern. If any car is waiting there, we end the green early.
     */
    private boolean carWaitingForNextPattern() throws IOException {
        if (yellow) return false;
        if (System.currentTimeMillis() - greenStartedAt < NIGHT_MIN_GREEN_SECONDS * 1000) {
            return false;
        }

        String[] nextPattern = PATTERNS[(pattern + 1) % PATTERNS.length];
        for (String lane : nextPattern) {
            if (multiplexor.carDetection(directionOf(lane), laneOf(lane))) return true;
        }
        return false;
    }

    // ---------------- Helpers ----------------

    /** Asks EMERGENCY for each direction. Returns where the vehicle is coming from, or null. */
    private Multiplexor.Direction detectEmergency() throws IOException {
        for (Multiplexor.Direction d : Multiplexor.Direction.values()) {
            if (multiplexor.emergency(d)) return d;
        }
        return null;
    }

    /**
     * Sets the given lanes to color and every other lane to red.
     * The reds go first on purpose, so two crossing directions are never
     * green at the same time, not even for a moment.
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

    /** Every traffic light red (showOnly with no lanes). */
    private void allRed() throws IOException {
        showOnly(new String[0], Multiplexor.SignalColor.RED);
    }

    /** Sends one SET_TRAFFIC_LIGHT. lane is written like "NORTH L". */
    private void setLight(String lane, Multiplexor.SignalColor color) throws IOException {
        Multiplexor.Lane l = laneOf(lane);

        // left and right lanes show an arrow, the straight lane is a full circle
        Multiplexor.Display display = switch (l) {
            case L -> Multiplexor.Display.LEFT_ARROW;
            case R -> Multiplexor.Display.RIGHT_ARROW;
            case C -> Multiplexor.Display.FULL;
        };
        multiplexor.setTrafficLight(directionOf(lane), l, display, color);
    }

    /** "NORTH L" gives NORTH */
    private Multiplexor.Direction directionOf(String lane) {
        return Multiplexor.Direction.valueOf(lane.split(" ")[0]);
    }

    /** "NORTH L" gives L */
    private Multiplexor.Lane laneOf(String lane) {
        return Multiplexor.Lane.valueOf(lane.split(" ")[1]);
    }

    /** Remembers when the current phase should end. step() checks it against the clock. */
    private void startTimer(double seconds) {
        phaseEndsAt = System.currentTimeMillis() + (long) (seconds * 1000);
    }
}
