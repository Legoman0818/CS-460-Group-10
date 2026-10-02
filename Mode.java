/**
 * Mode - every mode the intersection can be in.
 *
 * Design Diagram: the Mode Controller box, together with Controller.step().
 * Only the Controller changes the mode.
 *
 * ALL_RED_START - at startup or after the power comes back, all red for 2 seconds
 * DAY           - the normal cycle (green, yellow, next pattern)
 * NIGHT         - same cycle but greens are 20 seconds, and a green ends early
 *                 if a car is waiting for the next pattern
 * PED_CROSSING  - all red and WALK on, then back to DAY or NIGHT
 * EMERGENCY     - the emergency vehicle's direction is green, everything else red
 * NO_POWER      - power failure, the red lights blink until the power is back
 *
 * DAY and NIGHT are also the two settings of the Day/Night Timer.
 */
public enum Mode {
    ALL_RED_START, DAY, NIGHT, PED_CROSSING, EMERGENCY, NO_POWER
}
