/**
 * All the modes the intersection can be in. Only the Controller changes modes.
 *
 * ALL_RED_START - at startup or after power comes back, all red for a moment
 * DAY           - normal signal cycle (green, yellow, next pattern)
 * NIGHT         - same cycle but 20 second greens, a green ends early if a
 *                 car is waiting for the next pattern
 * PED_CROSSING  - all red and WALK on, then back to DAY or NIGHT
 * EMERGENCY     - the emergency vehicle's approach is green, everything else red
 * NO_POWER      - power failure, red lights blink until power is back
 *
 * DAY and NIGHT are also the two settings of the Day/Night Timer.
 */
public enum Mode {
    ALL_RED_START, DAY, NIGHT, PED_CROSSING, EMERGENCY, NO_POWER
}
