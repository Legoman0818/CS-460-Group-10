import java.util.Map;

/**
 * InductionSensor - the car sensors in the road. (Walkthrough 3.8)
 *
 * Design Diagram: the Induction Sensor box (Night mode uses it).
 *
 * API: CAR_DETECTION dir lane calls detect().
 *
 * There's one sensor loop at the stop line of each lane. It sees a car when
 * that lane's car is stopped at the line.
 */
public class InductionSensor {

    // the cars by lane, the keys look like "NORTH_LEFT" (see Multiplexor.laneKey)
    private final Map<String, Car> carsByLane;

    public InductionSensor(Map<String, Car> carsByLane) {
        this.carsByLane = carsByLane;
    }

    /** CAR_DETECTION: true if a car is waiting at the stop line of this lane. */
    public boolean detect(Multiplexor.Direction direction, Multiplexor.Lane lane) {
        Car car = carsByLane.get(Multiplexor.laneKey(direction, lane));
        return car != null && car.isWaitingAtStopLine();
    }
}
