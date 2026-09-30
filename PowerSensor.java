/**
 * Power Sensor device. When the power fails it makes every traffic light
 * blink red by itself, without going through the Controller. The Controller
 * finds out by checking POWER.
 */
public class PowerSensor {

    private boolean on = true;

    public boolean isOn() {
        return on;
    }

    /** Power failure: go to the failsafe (red lights blinking). */
    public void trip(TrafficLights trafficLights) {
        on = false;
        trafficLights.startBlinkingRed();
    }

    /** Power is back (after Reset). */
    public void restore(TrafficLights trafficLights) {
        on = true;
        trafficLights.stopBlinking();
    }
}
