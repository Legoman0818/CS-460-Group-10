import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Car - one car in the simulation. (Walkthrough 3.12)
 *
 * Design Diagram: not on the diagram. It's part of the simulation, not the
 * traffic system.
 *
 * There's one car per lane. It drives along its route, which is a list of
 * {x, y} points from Roads.ROUTES:
 *   point 0 = off the screen where it starts
 *   point 1 = the stop line
 *   last    = off the screen on the other side, then it starts over
 */
public class Car extends Group {

    private static final double SPEED = 90; // pixels per second

    private final double[][] route;            // list of {x, y} points
    private final TrafficLights trafficLights; // so we can check this lane's light
    private final String lane;                 // like "NORTH_LEFT"

    private double x;
    private double y;
    private int targetWaypoint; // which point the car is driving to right now

    public Car(double[][] route, Color color, TrafficLights trafficLights, String lane) {
        if (route == null || route.length < 3) {
            throw new IllegalArgumentException("A car route needs at least 3 waypoints");
        }

        this.route = route;
        this.trafficLights = trafficLights;
        this.lane = lane;

        // the car picture: a colored body and a windshield
        Rectangle body = new Rectangle(0, 0, 30, 52);
        body.setArcWidth(10);
        body.setArcHeight(10);
        body.setFill(color);
        body.setStroke(Color.WHITE);
        body.setStrokeWidth(2);

        Rectangle windshield = new Rectangle(5, 10, 20, 12);
        windshield.setArcWidth(5);
        windshield.setArcHeight(5);
        windshield.setFill(Color.web("#a9d7ff"));

        getChildren().addAll(body, windshield);
        reset();
    }

    /** Crosswalk calls this every frame. seconds is the time since the last frame. */
    public void update(double seconds) {
        // At the stop line: wait until the light is green, then go into the
        // intersection. Once it's in, it keeps going even if the light
        // changes, like a real car would.
        if (isWaitingAtStopLine()) {
            if (!trafficLights.isGreen(lane)) return;
            targetWaypoint++;
            faceNextWaypoint();
        }

        if (targetWaypoint >= route.length) {
            reset();
            return;
        }

        moveTowardTarget(SPEED * seconds);

        if (atTarget()) {
            // snap exactly onto the point so small errors don't add up
            x = route[targetWaypoint][0];
            y = route[targetWaypoint][1];
            updatePosition();

            // don't go past the stop line here, the check at the top handles that
            if (targetWaypoint != 1) {
                targetWaypoint++;
                if (targetWaypoint >= route.length) {
                    reset(); // drove off the screen, start over
                } else {
                    faceNextWaypoint();
                }
            }
        }
    }

    /** True if the car is stopped at the stop line. This is what the Induction Sensor checks. */
    public boolean isWaitingAtStopLine() {
        return targetWaypoint == 1 && atTarget();
    }

    /** Moves toward the target point, but not past it. */
    private void moveTowardTarget(double distance) {
        double dx = route[targetWaypoint][0] - x;
        double dy = route[targetWaypoint][1] - y;
        double remaining = Math.hypot(dx, dy);

        if (remaining == 0) return;

        double amount = Math.min(distance, remaining);
        x += dx / remaining * amount;
        y += dy / remaining * amount;
        updatePosition();
    }

    /** True if the car is on its target point. */
    private boolean atTarget() {
        if (targetWaypoint >= route.length) return true;
        return Math.hypot(route[targetWaypoint][0] - x,
                          route[targetWaypoint][1] - y) < 0.01;
    }

    /** Turns the car to face the point it's driving to. */
    private void faceNextWaypoint() {
        if (targetWaypoint >= route.length) return;

        double dx = route[targetWaypoint][0] - x;
        double dy = route[targetWaypoint][1] - y;

        // the car picture points down, so subtract 90 degrees
        setRotate(Math.toDegrees(Math.atan2(dy, dx)) - 90);
    }

    private void updatePosition() {
        setLayoutX(x);
        setLayoutY(y);
    }

    /** Puts the car back at the start of its route. */
    private void reset() {
        x = route[0][0];
        y = route[0][1];
        targetWaypoint = 1;
        updatePosition();
        faceNextWaypoint();
    }
}
