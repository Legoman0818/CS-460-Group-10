import javafx.animation.AnimationTimer;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * EmergencyVehicle - the ambulance animation.
 *
 * Design Diagram: not on the diagram. It's what sets off the Emergency
 * Vehicle Detector.
 *
 * It drives along the same route as the normal car in the lane it needs
 * (Crosswalk picks the lane with Roads.laneFor), and it doesn't stop for
 * lights. When it gets to the end it calls onFinished, which is
 * removeEmergencyVehicle in Crosswalk. That clears the detector, and the
 * Controller goes back to normal.
 */
public class EmergencyVehicle extends Group {

    private static final double SPEED = 190.0; // pixels per second

    private final double[][] route;    // list of {x, y} points
    private final Runnable onFinished; // what to run when it leaves the screen
    private AnimationTimer animation;
    private int target = 1;            // point 0 is the start, so drive to point 1 first
    private double x;
    private double y;

    public EmergencyVehicle(double[][] route, Runnable onFinished) {
        this.route = route;
        this.onFinished = onFinished;

        // drawn around (0,0) so it rotates around its center
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

    /** Starts moving. It has its own AnimationTimer, so it moves every frame. */
    public void play() {
        animation = new AnimationTimer() {
            private long previous;

            @Override
            public void handle(long now) {
                if (previous == 0) {
                    previous = now;
                    return;
                }
                double seconds = (now - previous) / 1_000_000_000.0; // now is in nanoseconds, so change it to seconds
                previous = now;
                move(SPEED * seconds);
            }
        };
        animation.start();
    }

    /** Stops moving (when it's finished, or on Reset / Power Off). */
    public void stop() {
        if (animation != null) animation.stop();
    }

    /** Moves toward the next point. When it reaches a point it turns toward the one after. */
    private void move(double distance) {
        if (target >= route.length) {
            finish();
            return;
        }
        double dx = route[target][0] - x;
        double dy = route[target][1] - y;
        double remaining = Math.hypot(dx, dy);

        if (remaining <= distance) {
            // reached the point, go to the next one
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

    /** Turns the vehicle to face the next point. */
    private void faceTarget() {
        double dx = route[target][0] - x;
        double dy = route[target][1] - y;
        // the picture points down, so subtract 90 degrees (same as Car)
        setRotate(Math.toDegrees(Math.atan2(dy, dx)) - 90);
    }

    private void updatePosition() {
        setLayoutX(x);
        setLayoutY(y);
    }

    private void finish() {
        stop();
        onFinished.run();
    }
}
