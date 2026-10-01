/**
 * PowerSensor - knows if the power is on. (Walkthrough 3.11)
 *
 * Design Diagram: not on the diagram yet. It's what drives the NO_POWER mode.
 *
 * API: the extra command POWER calls isOn().
 *
 * When the power fails it makes the lights blink red BY ITSELF, without
 * waiting for the Controller, like a real signal box would (that's the
 * failsafe). The Controller finds out the next time it checks POWER.
 */
public class PowerSensor {

    private boolean on = true;

    /** POWER */
    public boolean isOn() {
        return on;
    }

    /** Power Off button: power fails, every light blinks red. */
    public void trip(TrafficLights trafficLights) {
        on = false;
        trafficLights.startBlinkingRed();
    }

    /** Reset button: power is back, lights go solid red. The Controller then restarts with ALL_RED_START. */
    public void restore(TrafficLights trafficLights) {
        on = true;
        trafficLights.stopBlinking();
    }
}
