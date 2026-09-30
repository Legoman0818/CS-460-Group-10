import java.util.ArrayList;
import java.util.List;

import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;

/**
 * Pedestrian Call Button and Pedestrian Lights devices.
 *
 * Lights: 5 stick figure signs (4 corners and the middle). They all show
 * WALK (orange) or STOP (white) together.
 *
 * Button: clicking a corner sign presses the button. The press is saved until
 * the Controller clears it with PED_CLEAR_REQUEST.
 */
public class Pedestrian {

    private static final Color PAINT  = Color.web("#f0f0f0");
    private static final Color ORANGE = Color.web("#ff8c1a");

    private final Color roadBackground;
    private final List<PedSign> signs = new ArrayList<>();
    private boolean requested = false;

    /** onCornerPressed runs when a corner sign is clicked (after the press is saved). */
    public Pedestrian(Pane root, Color roadBackground, Runnable onCornerPressed) {
        this.roadBackground = roadBackground;
        Runnable onPressed = () -> {
            press();
            onCornerPressed.run();
        };
        sign(root, 250, 198, false, onPressed); // north-west corner
        sign(root, 783, 210, false, onPressed); // north-east corner
        sign(root, 250, 765, false, onPressed); // south-west corner
        sign(root, 785, 765, false, onPressed); // south-east corner
        // the middle one has no button and a solid background so the
        // diagonal crosswalk lines don't show through it
        sign(root, 514, 480, true, null);
    }

    // ---------------- button ----------------

    public void press() {
        requested = true;
    }

    public boolean isRequested() {
        return requested;
    }

    /** Clears the press. Returns true if there was one. */
    public boolean clearRequest() {
        boolean wasRequested = requested;
        requested = false;
        return wasRequested;
    }

    // ---------------- lights ----------------

    /** true = WALK, false = STOP */
    public void setWalkLight(boolean walk) {
        for (PedSign sign : signs) {
            if (walk) sign.walk();
            else sign.stop();
        }
    }

    /** Draws one sign: a box with a stick figure in it. */
    private void sign(Pane root, double cx, double cy, boolean middle, Runnable onPressed) {
        double half = 26;
        Rectangle box = new Rectangle(cx - half, cy - half, half * 2, half * 2);
        Color normalFill = middle ? roadBackground : Color.TRANSPARENT;
        box.setFill(normalFill);
        box.setStroke(PAINT);
        box.setStrokeWidth(2);
        box.setPickOnBounds(true);
        if (onPressed != null) {
            box.setCursor(Cursor.HAND);
            box.setOnMouseClicked(e -> onPressed.run());
        }

        Circle head = new Circle(cx, cy - 11, 6);
        head.setStroke(PAINT);
        head.setStrokeWidth(2);
        head.setFill(Color.TRANSPARENT);
        Line body = line(cx, cy - 5, cx, cy + 6);
        Line arms = line(cx - 8, cy - 1, cx + 8, cy - 1);
        Line legL = line(cx, cy + 6, cx - 7, cy + 17);
        Line legR = line(cx, cy + 6, cx + 7, cy + 17);

        Group g = new Group(box, head, body, arms, legL, legR);
        signs.add(new PedSign(normalFill, box, head, body, arms, legL, legR));
        root.getChildren().add(g);
        if (middle) {
            g.toFront(); // so cars and road lines can't cover it
        }
    }

    private Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(2);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    /** The shapes that make up one sign, so they can be recolored together. */
    private static final class PedSign {
        private final Color normalFill;
        private final Shape[] parts;

        PedSign(Color normalFill, Shape... parts) {
            this.normalFill = normalFill;
            this.parts = parts;
        }

        /** WALK: orange */
        void walk() {
            for (Shape s : parts) {
                s.setStroke(ORANGE);
                if (s instanceof Rectangle box) {
                    // the middle sign stays solid so the crosswalk lines stay hidden
                    box.setFill(normalFill.equals(Color.TRANSPARENT)
                            ? Color.web("#ff8c1a33")
                            : Color.web("#5a2a00"));
                }
            }
        }

        /** STOP: back to white */
        void stop() {
            for (Shape s : parts) {
                s.setStroke(PAINT);
                if (s instanceof Rectangle box) {
                    box.setFill(normalFill);
                }
            }
        }
    }
}
