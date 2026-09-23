import javafx.animation.AnimationTimer;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Animated emergency vehicle used by the JavaFX digital twin.
 *
 * Crosswalk supplies an ordered list of x/y waypoints. The vehicle moves
 * toward one waypoint at a time and rotates whenever the next segment changes
 * direction. The turning routes therefore appear as clear 90-degree turns.
 */
public class EmergencyVehicle extends Group {
    // Movement is measured in JavaFX pixels per second.
    private static final double SPEED = 190.0;

    private final double[][] route; // ordered {x, y} waypoints
    private final Runnable onFinished; // tells Crosswalk the route has ended
    private AnimationTimer animation; // calls move() once per visible frame
    private int target = 1; // route[0] is the spawn point; route[1] is first goal
    private double x; // current center position
    private double y;

    /** Builds the vehicle image and places it at the first route point. */
    public EmergencyVehicle(double[][] route, Runnable onFinished) {
        this.route = route;
        this.onFinished = onFinished;

        // Coordinates are centered around (0,0), which makes rotation natural.
        Rectangle body = new Rectangle(-18, -30, 36, 60);
        body.setArcWidth(10);
        body.setArcHeight(10);
        body.setFill(Color.WHITE);
        body.setStroke(Color.web("#cbd5e1"));
        body.setStrokeWidth(2);

        Rectangle windshield = new Rectangle(-13, -18, 26, 13);
        windshield.setArcWidth(5);
        windshield.setArcHeight(5);
        windshield.setFill(Color.web("#9bd7ff"));

        // The roof lights are static markers; they do not flash.
        Rectangle stripe = new Rectangle(-18, 4, 36, 9);
        stripe.setFill(Color.web("#dc2626"));
        Circle redLight = new Circle(-7, -3, 5, Color.RED);
        Circle blueLight = new Circle(7, -3, 5, Color.DODGERBLUE);
        getChildren().addAll(body, windshield, stripe, redLight, blueLight);

        x = route[0][0];
        y = route[0][1];
        updatePosition();
        faceTarget();
    }

    /** Starts frame-by-frame movement along the route. */
    public void play() {
        animation = new AnimationTimer() {
            private long previous;

            @Override
            public void handle(long now) {
                if (previous == 0) {
                    previous = now;
                    return;
                }
                // AnimationTimer gives nanoseconds; convert them to seconds.
                double seconds = (now - previous) / 1_000_000_000.0;
                previous = now;
                move(SPEED * seconds);
            }
        };
        animation.start();
    }

    /** Stops future animation frames without removing the vehicle node. */
    public void stop() {
        if (animation != null) animation.stop();
    }

    /** Moves toward the current target without overshooting the waypoint. */
    private void move(double distance) {
        if (target >= route.length) {
            finish();
            return;
        }
        double dx = route[target][0] - x;
        double dy = route[target][1] - y;
        double remaining = Math.hypot(dx, dy);

        if (remaining <= distance) {
            // Snap exactly onto the waypoint before selecting the next segment.
            x = route[target][0];
            y = route[target][1];
            target++;
            updatePosition();
            if (target >= route.length) finish();
            else faceTarget();
            return;
        }

        x += dx / remaining * distance;
        y += dy / remaining * distance;
        updatePosition();
    }

    /** Rotates the vehicle so its front points toward the next waypoint. */
    private void faceTarget() {
        double dx = route[target][0] - x;
        double dy = route[target][1] - y;
        // The drawing faces upward at 0 degrees, so subtract 90 degrees.
        setRotate(Math.toDegrees(Math.atan2(dy, dx)) - 90);
    }

    /** Copies the route coordinates into the JavaFX node position. */
    private void updatePosition() {
        setLayoutX(x);
        setLayoutY(y);
    }

    /** Stops movement and lets Crosswalk restore normal traffic operation. */
    private void finish() {
        stop();
        onFinished.run();
    }
}
