import java.util.Map;

/**
 * Induction Sensor device. There is one sensor loop at the stop line of each
 * lane. It detects a car when that lane's car is stopped at the line.
 */
public class InductionSensor {

    // keys look like "NORTH_LEFT" (see Multiplexor.laneKey)
    private final Map<String, Car> carsByLane;

    public InductionSensor(Map<String, Car> carsByLane) {
        this.carsByLane = carsByLane;
    }

    /** True if a car is waiting at the stop line of this lane. */
    public boolean detect(Multiplexor.Direction direction, Multiplexor.Lane lane) {
        Car car = carsByLane.get(Multiplexor.laneKey(direction, lane));
        return car != null && car.isWaitingAtStopLine();
    }
}
