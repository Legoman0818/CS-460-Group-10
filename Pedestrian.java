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
 * Pedestrian - the call button and the walk lights.
 *
 * Design Diagram: the Pedestrian box, which covers both the Pedestrian Call
 * Button and the Pedestrian Lights.
 *
 * API: PED_REQUEST calls isRequested(), PED_CLEAR_REQUEST calls
 * clearRequest(), and SET_PED_LIGHT calls setWalkLight().
 *
 * Lights: 5 stick figure signs (the 4 corners and the middle). They all
 * show WALK (orange) or STOP (white) at the same time.
 * Button: clicking a corner sign presses the button.
 */
public class Pedestrian {

    private static final Color PAINT  = Color.web("#f0f0f0");
    private static final Color ORANGE = Color.web("#ff8c1a");

    private final List<PedSign> signs = new ArrayList<>();
    private boolean requested = false; // true once someone presses the button

    /** onCornerPressed runs when a corner sign is clicked (after the press is saved). */
    public Pedestrian(Pane root, Runnable onCornerPressed) {
        sign(root, 250, 198, false, onCornerPressed); // north-west corner
        sign(root, 783, 210, false, onCornerPressed); // north-east corner
        sign(root, 250, 765, false, onCornerPressed); // south-west corner
        sign(root, 785, 765, false, onCornerPressed); // south-east corner
        // the middle one has no button, and a solid background so the
        // diagonal crosswalk lines don't show through it
        sign(root, 514, 480, true, null);
    }

    //  Button 
    // A press stays saved until the Controller clears it, so it never gets
    // lost, even if an emergency happens in between.

    public void press() {
        requested = true;
    }

    /** PED_REQUEST */
    public boolean isRequested() {
        return requested;
    }

    /** PED_CLEAR_REQUEST: clears the press. True if there was one. */
    public boolean clearRequest() {
        boolean wasRequested = requested;
        requested = false;
        return wasRequested;
    }

    //  Lights 

    /** SET_PED_LIGHT: true = WALK (orange), false = STOP (white). */
    public void setWalkLight(boolean walk) {
        for (PedSign sign : signs) {
            if (walk) sign.walk();
            else sign.stop();
        }
    }

    /** Draws one sign: a box with a stick figure in it. Corner signs are the buttons. */
    private void sign(Pane root, double cx, double cy, boolean middle, Runnable onPressed) {
        double half = 26;
        Rectangle box = new Rectangle(cx - half, cy - half, half * 2, half * 2);
        box.setFill(middle ? Roads.BG : Color.TRANSPARENT);
        box.setStroke(PAINT);
        box.setStrokeWidth(2);
        box.setPickOnBounds(true);
        if (onPressed != null) {
            box.setCursor(Cursor.HAND);
            box.setOnMouseClicked(e -> {
                press();
                onPressed.run();
            });
        }

        // the stick figure
        Circle head = new Circle(cx, cy - 11, 6);
        head.setStroke(PAINT);
        head.setStrokeWidth(2);
        head.setFill(Color.TRANSPARENT);
        Line body = line(cx, cy - 5, cx, cy + 6);
        Line arms = line(cx - 8, cy - 1, cx + 8, cy - 1);
        Line legL = line(cx, cy + 6, cx - 7, cy + 17);
        Line legR = line(cx, cy + 6, cx + 7, cy + 17);

        Group g = new Group(box, head, body, arms, legL, legR);
        signs.add(new PedSign(box, middle, new Shape[] {box, head, body, arms, legL, legR}));
        root.getChildren().add(g);
        if (middle) {
            g.toFront(); // so the cars and road lines can't cover it
        }
    }

    private Line line(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(2);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    /** The shapes that make up one sign, so we can recolor them all together. */
    private static final class PedSign {
        private final Rectangle box;
        private final boolean middle;
        private final Shape[] parts; // the box and the stick figure

        PedSign(Rectangle box, boolean middle, Shape[] parts) {
            this.box = box;
            this.middle = middle;
            this.parts = parts;
        }

        /** WALK: orange */
        void walk() {
            for (Shape s : parts) s.setStroke(ORANGE);
            // the middle sign stays solid so the crosswalk lines stay hidden
            if (middle) box.setFill(Color.web("#5a2a00"));
            else box.setFill(Color.web("#ff8c1a33"));
        }

        /** STOP: back to white */
        void stop() {
            for (Shape s : parts) s.setStroke(PAINT);
            if (middle) box.setFill(Roads.BG);
            else box.setFill(Color.TRANSPARENT);
        }
    }
}
