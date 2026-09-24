/**
 * Pedestrian Call Button device.
 *
 * A press is latched until something reads and clears it, the same way a
 * real momentary-contact push button holds an interrupt line high until the
 * handler services it. Cross Walk calls press() when a corner pedestrian
 * sign is clicked; the PED_REQUEST protocol command calls testAndClear() to
 * read and reset it.
 */
public class PedestrianCallButton {

    private boolean requested;

    /** Latches a pending crossing request. */
    public synchronized void press() {
        requested = true;
    }

    /** Reads and clears the pending request, like a real interrupt handler. */
    public synchronized boolean testAndClear() {
        boolean value = requested;
        requested = false;
        return value;
    }
}
