import java.util.function.BooleanSupplier;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Represents one car in the traffic simulation.
 *
 * PRESENTATION GUIDE
 * ------------------
 * The route is a list of x/y waypoints supplied by Crosswalk.java.
 * Waypoint 0 is the spawn point and waypoint 1 is always the stop line.
 * update() moves the car toward the next waypoint once per JavaFX frame.
 * reset() sends the car back to its spawn point after it finishes the route.
 */
public class Car extends Group {

    private static final double SPEED = 90; // pixels per second

    private final double[][] route;
    private final BooleanSupplier hasGreenLight;

    private double x;
    private double y;
    private int targetWaypoint;
    private boolean enteredIntersection;

    public Car(double[][] route, Color color, BooleanSupplier hasGreenLight) {
        if (route == null || route.length < 3) {
            throw new IllegalArgumentException("A car route needs at least 3 waypoints");
        }

        this.route = route;
        this.hasGreenLight = hasGreenLight;

        // The visible car is built from a rounded body and a windshield.
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

    /** Called by Crosswalk's AnimationTimer for every visible frame. */
    public void update(double seconds) {
        /*
         * THIS IS THE TRAFFIC-LIGHT RULE:
         * Waypoint 1 is the stop line. Red and yellow make the car wait there.
         * Once green lets it enter, later color changes do not trap it inside.
         */
        if (targetWaypoint == 1 && atTarget() && !hasGreenLight.getAsBoolean()) {
            return;
        }

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
            // Snap onto the point so tiny decimal errors do not build up.
            x = route[targetWaypoint][0];
            y = route[targetWaypoint][1];
            updatePosition();

            // Stop at waypoint 1 until green; pass all later waypoints normally.
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

    private void moveTowardTarget(double distance) {
        double targetX = route[targetWaypoint][0];
        double targetY = route[targetWaypoint][1];
        double dx = targetX - x;
        double dy = targetY - y;
        double remaining = Math.hypot(dx, dy);

        if (remaining == 0) return;

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

    private void faceNextWaypoint() {
        if (targetWaypoint >= route.length) return;

        double dx = route[targetWaypoint][0] - x;
        double dy = route[targetWaypoint][1] - y;

        // The car drawing naturally points down, so subtract 90 degrees.
        setRotate(Math.toDegrees(Math.atan2(dy, dx)) - 90);
    }

    private void updatePosition() {
        setLayoutX(x);
        setLayoutY(y);
    }

    private void reset() {
        x = route[0][0];
        y = route[0][1];
        targetWaypoint = 1;
        enteredIntersection = false;
        updatePosition();
        faceNextWaypoint();
    }
}
