/**
 * Power Sensor device.
 *
 * Unlike the other devices, its failsafe path bypasses Cross Walk's normal
 * command dispatch and reaches Traffic Lights directly, the way a real
 * power-loss circuit forces signals dark without waiting on the controller.
 * Controller only learns about the outcome afterward, through the ordinary
 * POWER_FAILURE / RESET protocol commands.
 */
public class PowerSensor {

    private boolean on = true;

    public boolean isOn() {
        return on;
    }

    /** Trips the failsafe: goes dark and forces every traffic light red. */
    public void trip(TrafficLights trafficLights) {
        on = false;
        trafficLights.allRed();
    }

    /** Restores power after a reset. */
    public void restore() {
        on = true;
    }
}
