import java.util.function.BooleanSupplier;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * One car in the simulation. It drives along a list of x/y points (its route).
 * Point 0 is where it starts and point 1 is the stop line. It waits at the
 * stop line until its light is green. At the end of the route it starts over.
 */
public class Car extends Group {

    private static final double SPEED = 90; // pixels per second

    private final double[][] route;              // list of {x, y} points
    private final BooleanSupplier hasGreenLight; // checks this lane's light

    private double x;
    private double y;
    private int targetWaypoint;          // the point the car is driving to
    private boolean enteredIntersection; // once it's in, it keeps going

    public Car(double[][] route, Color color, BooleanSupplier hasGreenLight) {
        if (route == null || route.length < 3) {
            throw new IllegalArgumentException("A car route needs at least 3 waypoints");
        }

        this.route = route;
        this.hasGreenLight = hasGreenLight;

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

    /** Called every frame. seconds is the time since the last frame. */
    public void update(double seconds) {
        // At the stop line: wait if the light isn't green.
        if (targetWaypoint == 1 && atTarget() && !hasGreenLight.getAsBoolean()) {
            return;
        }

        // At the stop line and green: go into the intersection. After this the
        // car doesn't stop even if the light changes.
        if (targetWaypoint == 1 && atTarget() && hasGreenLight.getAsBoolean()) {
            enteredIntersection = true;
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

            // don't go past the stop line here, the check at the top handles it
            if (targetWaypoint != 1 || enteredIntersection) {
                targetWaypoint++;
                if (targetWaypoint >= route.length) {
                    reset();
                } else {
                    faceNextWaypoint();
                }
            }
        }
    }

    /** True if the car is stopped at the stop line (used by the Induction Sensor). */
    public boolean isWaitingAtStopLine() {
        return targetWaypoint == 1 && !enteredIntersection && atTarget();
    }

    private void moveTowardTarget(double distance) {
        double dx = route[targetWaypoint][0] - x;
        double dy = route[targetWaypoint][1] - y;
        double remaining = Math.hypot(dx, dy);

        if (remaining == 0) return;

        // don't move past the point
        double amount = Math.min(distance, remaining);
        x += dx / remaining * amount;
        y += dy / remaining * amount;
        updatePosition();
    }

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
        enteredIntersection = false;
        updatePosition();
        faceNextWaypoint();
    }
}
