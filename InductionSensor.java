import java.util.Map;

/**
 * Induction Sensor device.
 *
 * Each approach lane has one simulated wire loop embedded at its stop line.
 * A real loop detects the metal mass of a vehicle sitting over it; here the
 * equivalent signal is Car.isWaitingAtStopLine(), so this class reports a
 * lane as occupied exactly when that lane's Car object is actually stopped
 * at the line, instead of returning a hard-coded value.
 */
public class InductionSensor {

    private final Map<String, Car> carsByLane;

    /** carsByLane is keyed by Multiplexor.laneKey(direction, lane), e.g. "NORTH_LEFT". */
    public InductionSensor(Map<String, Car> carsByLane) {
        this.carsByLane = carsByLane;
    }

    /** True while a car is stopped at the given lane's stop line. */
    public boolean detect(Multiplexor.Direction direction, Multiplexor.Lane lane) {
        Car car = carsByLane.get(Multiplexor.laneKey(direction, lane));
        return car != null && car.isWaitingAtStopLine();
    }
}
