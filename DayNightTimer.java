/**
 * DayNightTimer - says if it's day or night.
 *
 * The input that picks between the Day and Night boxes.
 *
 * API: the extra commands DAY_NIGHT (get) and GREEN_INTERVAL (getIntervalSeconds).
 *
 * Holds two values: DAY or NIGHT, and how long a day green lasts. Both get
 * set from the side panel.
 */
public class DayNightTimer {

    private static final double DEFAULT_INTERVAL_SECONDS = 15.0;

    private Mode mode = Mode.DAY; // only ever DAY or NIGHT
    private double intervalSeconds = DEFAULT_INTERVAL_SECONDS;

    public void set(Mode mode) {
        this.mode = mode;
    }

    /** DAY_NIGHT */
    public Mode get() {
        return mode;
    }

    public boolean isDay() {
        return mode == Mode.DAY;
    }

    /** GREEN_INTERVAL */
    public double getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(double intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
}
