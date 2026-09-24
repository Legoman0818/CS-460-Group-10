/**
 * Day/Night Timer device.
 *
 * Holds which lighting program is currently active. A real timer or
 * photocell would flip this automatically at dawn and dusk; in the
 * simulation the panel's mode switch sets it directly through SET_MODE.
 */
public class DayNightTimer {

    // The base green interval a real day/night timer would apply to each
    // signal pattern. The panel's "Apply Interval" field changes this.
    private static final double DEFAULT_INTERVAL_SECONDS = 15.0;

    private Multiplexor.Mode mode = Multiplexor.Mode.DAY;
    private double intervalSeconds = DEFAULT_INTERVAL_SECONDS;

    public void set(Multiplexor.Mode mode) {
        this.mode = mode;
    }

    public Multiplexor.Mode get() {
        return mode;
    }

    public boolean isDay() {
        return mode == Multiplexor.Mode.DAY;
    }

    public double getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(double intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
}
