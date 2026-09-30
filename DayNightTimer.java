/**
 * Day/Night Timer device. Holds whether it is day or night and how long each
 * green light lasts. In the simulation these are set from the side panel.
 * The Controller reads them with DAY_NIGHT and GREEN_INTERVAL.
 */
public class DayNightTimer {

    private static final double DEFAULT_INTERVAL_SECONDS = 15.0;

    private Mode mode = Mode.DAY; // only ever DAY or NIGHT
    private double intervalSeconds = DEFAULT_INTERVAL_SECONDS;

    public void set(Mode mode) {
        this.mode = mode;
    }

    public Mode get() {
        return mode;
    }

    public boolean isDay() {
        return mode == Mode.DAY;
    }

    public double getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(double intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }
}
