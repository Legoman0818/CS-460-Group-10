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
 * Pedestrian Lights device.
 *
 * Owns the five stick-figure signs (four corners plus the protected center
 * island) that all share one WALK/STOP state. Cross Walk calls setWalk(...)
 * whenever the twin receives SET_PED_LIGHT. The four corner signs are also
 * clickable; a click is reported through onCornerPressed so Cross Walk can
 * forward it to the Pedestrian Call Button.
 */
public class PedestrianLights {

    private static final Color PAINT  = Color.web("#f0f0f0");
    private static final Color ORANGE = Color.web("#ff8c1a");

    private final Color roadBackground;
    private final List<PedZone> zones = new ArrayList<>();
    private boolean walk = false;

    public PedestrianLights(Pane root, Color roadBackground, Runnable onCornerPressed) {
        this.roadBackground = roadBackground;
        zone(root, 250, 198, false, onCornerPressed); // north-west corner
        zone(root, 783, 210, false, onCornerPressed); // north-east corner
        zone(root, 250, 765, false, onCornerPressed); // south-west corner
        zone(root, 785, 765, false, onCornerPressed); // south-east corner
        // The center control has no physical button and uses a solid panel so
        // the white diagonal crosswalk stripes cannot show through it.
        zone(root, 514, 480, true, null);
    }

    public boolean isWalk() {
        return walk;
    }

    /** Changes every pedestrian sign together. */
    public void setWalk(boolean active) {
        walk = active;
        for (PedZone zone : zones) {
            if (active) zone.walk();
            else zone.clear();
        }
    }

    /** Builds one pedestrian sign from a box and a simple stick figure. */
    private void zone(Pane root, double cx, double cy, boolean centerControl, Runnable onPressed) {
        double half = 26;
        Rectangle box = new Rectangle(cx - half, cy - half, half * 2, half * 2);
        Color normalFill = centerControl ? roadBackground : Color.TRANSPARENT;
        box.setFill(normalFill);
        box.setStroke(PAINT);
        box.setStrokeWidth(2);
        box.setPickOnBounds(true);
        if (onPressed != null) {
            box.setCursor(Cursor.HAND);
            box.setOnMouseClicked(e -> onPressed.run());
        }

        Color figureColor = PAINT;
        Circle head = new Circle(cx, cy - 11, 6);
        head.setStroke(figureColor);
        head.setStrokeWidth(2);
        head.setFill(Color.TRANSPARENT);
        Line body = strokeLine(cx, cy - 5, cx, cy + 6);
        Line arms = strokeLine(cx - 8, cy - 1, cx + 8, cy - 1);
        Line legL = strokeLine(cx, cy + 6, cx - 7, cy + 17);
        Line legR = strokeLine(cx, cy + 6, cx + 7, cy + 17);

        Group g = new Group(box, head, body, arms, legL, legR);
        zones.add(new PedZone(normalFill, figureColor, box, head, body, arms, legL, legR));
        root.getChildren().add(g);
        if (centerControl) {
            // Guarantee that cars, road markings, and signals cannot cover it.
            g.toFront();
        }
    }

    /** Creates the rounded white lines used to build pedestrian figures. */
    private Line strokeLine(double x1, double y1, double x2, double y2) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(2);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        return l;
    }

    // Stores the shapes that form one pedestrian sign and recolors them as a group.
    private static final class PedZone {
        private final Color normalFill;
        private final Color normalStroke;
        private final Shape[] parts;

        PedZone(Color normalFill, Color normalStroke, Shape... parts) {
            this.normalFill = normalFill;
            this.normalStroke = normalStroke;
            this.parts = parts;
        }

        /** Shows the active crossing state in orange. */
        void walk() {
            for (Shape s : parts) {
                s.setStroke(ORANGE);
                if (s instanceof Rectangle r) {
                    // Keep the center island opaque even while its walk signal is on.
                    r.setFill(normalFill.equals(Color.TRANSPARENT)
                              ? Color.web("#ff8c1a33")
                              : Color.web("#5a2a00"));
                }
            }
        }

        /** Restores the normal white pedestrian sign. */
        void clear() {
            for (Shape s : parts) {
                s.setStroke(normalStroke);
                if (s instanceof Rectangle r) {
                    r.setFill(normalFill);
                }
            }
        }
    }
}
