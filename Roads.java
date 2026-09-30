import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Draws the road (lanes, stop lines, crosswalks, arrows) and holds the route
 * each lane's cars drive along. This is only the background picture; the
 * lights and cars are drawn by other classes on top of it.
 */
public class Roads {

    // size of the intersection picture
    public static final double W = 1024;
    public static final double H = 945;

    public static final Color BG    = Color.web("#0d0d0d"); // road color
    private static final Color PAINT = Color.web("#f0f0f0"); // white paint

    /**
     * The route for each lane, as a list of {x, y} points. Point 0 is off the
     * screen where cars start, point 1 is the stop line, and the last point is
     * off the screen on the other side. Keys look like "NORTH_LEFT".
     * The emergency vehicle uses these same routes.
     */
    public static final Map<String, double[][]> ROUTES = new LinkedHashMap<>();

    static {
        // NORTH approach, cars come in from the top
        ROUTES.put("NORTH_LEFT",     new double[][]{{438,-60},{438,140},{438,520},{W+80,520}});
        ROUTES.put("NORTH_STRAIGHT", new double[][]{{374,-60},{374,140},{374,H+80}});
        ROUTES.put("NORTH_RIGHT",    new double[][]{{310,-60},{310,140},{310,400},{-80,400}});

        // SOUTH approach, from the bottom
        ROUTES.put("SOUTH_LEFT",     new double[][]{{560,H+60},{560,765},{560,400},{-80,400}});
        ROUTES.put("SOUTH_STRAIGHT", new double[][]{{624,H+60},{624,765},{624,-80}});
        ROUTES.put("SOUTH_RIGHT",    new double[][]{{688,H+60},{688,765},{688,520},{W+80,520}});

        // WEST approach, from the left
        ROUTES.put("WEST_LEFT",      new double[][]{{-60,533},{185,533},{560,533},{560,-80}});
        ROUTES.put("WEST_STRAIGHT",  new double[][]{{-60,580},{185,580},{W+80,580}});
        ROUTES.put("WEST_RIGHT",     new double[][]{{-60,627},{185,627},{438,627},{438,H+80}});

        // EAST approach, from the right
        ROUTES.put("EAST_LEFT",      new double[][]{{W+60,405},{800,405},{438,405},{438,H+80}});
        ROUTES.put("EAST_STRAIGHT",  new double[][]{{W+60,345},{800,345},{-80,345}});
        ROUTES.put("EAST_RIGHT",     new double[][]{{W+60,285},{800,285},{560,285},{560,-80}});
    }

    /** Which lane a car from approach has to be in to get to destination. */
    public static Multiplexor.Lane laneFor(Multiplexor.Direction approach,
                                           Multiplexor.Direction destination) {
        Multiplexor.Direction left = switch (approach) {
            case NORTH -> Multiplexor.Direction.EAST;
            case SOUTH -> Multiplexor.Direction.WEST;
            case EAST  -> Multiplexor.Direction.SOUTH;
            case WEST  -> Multiplexor.Direction.NORTH;
        };
        Multiplexor.Direction right = switch (approach) {
            case NORTH -> Multiplexor.Direction.WEST;
            case SOUTH -> Multiplexor.Direction.EAST;
            case EAST  -> Multiplexor.Direction.NORTH;
            case WEST  -> Multiplexor.Direction.SOUTH;
        };
        if (destination == left) return Multiplexor.Lane.L;
        if (destination == right) return Multiplexor.Lane.R;
        return Multiplexor.Lane.C;
    }

    private final Pane root;

    /** Draws everything. Order matters, later things are drawn on top. */
    public Roads(Pane root) {
        this.root = root;
        background();
        stopLines();
        roads();
        crosswalks();
        laneArrows();
    }

    private void add(Node... nodes) {
        root.getChildren().addAll(nodes);
    }

    /** A solid white line. */
    private Line paint(double x1, double y1, double x2, double y2, double w) {
        Line l = new Line(x1, y1, x2, y2);
        l.setStroke(PAINT);
        l.setStrokeWidth(w);
        l.setStrokeLineCap(StrokeLineCap.BUTT);
        return l;
    }

    /** A dashed white line. */
    private Line dashed(double x1, double y1, double x2, double y2, double w, double on, double off) {
        Line l = paint(x1, y1, x2, y2, w);
        l.getStrokeDashArray().addAll(on, off);
        return l;
    }

    private void background() {
        Rectangle bg = new Rectangle(0, 0, W, H);
        bg.setFill(BG);
        add(bg);
    }

    /** Road edges, center lines, and the lane lines. */
    private void roads() {
        // corners of the intersection box
        double xL = 293, xR = 735, yT = 246, yB = 713;

        // road edges above and below
        add(paint(xL, 0, xL, yT, 3));
        add(paint(xR, 0, xR, yT, 3));
        add(paint(xL, yB, xL, H, 3));
        add(paint(xR, yB, xR, H, 3));

        // road edges left and right
        add(paint(0, yT, xL, yT, 3));
        add(paint(0, yB, xL, yB, 3));
        add(paint(xR, yT, W, yT, 3));
        add(paint(xR, yB, W, yB, 3));

        // center lines
        add(dashed(514, 0, 514, yT, 3, 22, 18));
        add(dashed(514, yB, 514, H, 3, 22, 18));
        add(dashed(0, 480, xL, 480, 3, 22, 18));
        add(dashed(xR, 480, W, 480, 3, 22, 18));

        // lane boxes that the arrows sit in (3 lanes each)
        // left
        for (double y : new double[]{486, 559, 632, 705}) add(paint(0, y, 210, y, 2));
        add(paint(210, 486, 210, 705, 2));
        // right
        for (double y : new double[]{252, 324, 396, 468}) add(paint(812, y, W, y, 2));
        add(paint(812, 252, 812, 468, 2));
        // top
        for (double x : new double[]{293, 357, 421, 485}) add(paint(x, 0, x, 168, 2));
        add(paint(293, 168, 485, 168, 2));
        // bottom
        for (double x : new double[]{543, 607, 671, 735}) add(paint(x, 775, x, H, 2));
        add(paint(543, 775, 735, 775, 2));
    }

    /** The 4 outside crosswalks and the X shaped one in the middle. */
    private void crosswalks() {
        crosswalkTicks(318, 709, 200, 232, true);   // top
        crosswalkTicks(318, 709, 724, 756, true);   // bottom
        crosswalkTicks(246, 272, 292, 686, false);  // left
        crosswalkTicks(762, 788, 292, 686, false);  // right

        // diagonal crosswalks through the middle
        diagonalCrosswalk(315, 265, 713, 695);
        diagonalCrosswalk(713, 265, 315, 695);
    }

    /** Draws short stripes across a diagonal line. */
    private void diagonalCrosswalk(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        // direction at 90 degrees to the line, for the stripes
        double px = -dy / length;
        double py = dx / length;

        int stripeCount = 23;
        double halfStripe = 12;
        for (int i = 0; i <= stripeCount; i++) {
            double t = i / (double) stripeCount;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            add(paint(x - px * halfStripe, y - py * halfStripe,
                      x + px * halfStripe, y + py * halfStripe, 4));
        }
    }

    /** Ladder style crosswalk. If vertical is true the stripes go up and down. */
    private void crosswalkTicks(double a1, double a2, double b1, double b2, boolean vertical) {
        int n = 26;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            if (vertical) {
                double x = a1 + t * (a2 - a1);
                add(paint(x, b1, x, b2, 3));
            } else {
                double y = b1 + t * (b2 - b1);
                add(paint(a1, y, a2, y, 3));
            }
        }
    }

    /** The thick lines where cars stop. */
    private void stopLines() {
        add(paint(293, 198, 514, 198, 6)); // north
        add(paint(514, 758, 735, 758, 6)); // south
        add(paint(244, 480, 244, 713, 6)); // west
        add(paint(790, 246, 790, 480, 6)); // east
    }

    /** The white arrows painted in each lane. */
    private void laneArrows() {
        // top, cars go down
        laneArrow(325, 12, 0, 1, -1, 0);
        laneArrow(389, 12, 0, 1, 0, 0);
        laneArrow(453, 12, 0, 1, 1, 0);
        // bottom, cars go up
        laneArrow(575, 933, 0, -1, -1, 0);
        laneArrow(639, 933, 0, -1, 0, 0);
        laneArrow(703, 933, 0, -1, 1, 0);
        // left, cars go right
        laneArrow(6, 548, 1, 0, 0, -1, 74, 44, 150);
        laneArrow(6, 595, 1, 0, 0,  0, 74, 44, 150);
        laneArrow(6, 642, 1, 0, 0,  1, 74, 44, 150);
        // right, cars go left
        laneArrow(1018, 300, -1, 0, 0, -1, 74, 40, 150);
        laneArrow(1018, 360, -1, 0, 0,  0, 74, 40, 150);
        laneArrow(1018, 420, -1, 0, 0,  1, 74, 40, 150);
    }

    private void laneArrow(double ox, double oy, double fx, double fy, double cx, double cy) {
        laneArrow(ox, oy, fx, fy, cx, cy, 92, 24, 140);
    }

    /**
     * Draws one lane arrow.
     * (ox, oy) is the tail, (fx, fy) is the direction it goes, and (cx, cy) is
     * the direction it turns at the end. (0, 0) means a straight arrow.
     */
    private void laneArrow(double ox, double oy, double fx, double fy, double cx, double cy,
                           double run, double turn, double straightLen) {
        Path p = new Path();
        p.setStroke(PAINT);
        p.setFill(null);
        p.setStrokeWidth(5);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);

        if (cx == 0 && cy == 0) {
            double ex = ox + fx * straightLen, ey = oy + fy * straightLen;
            p.getElements().add(new MoveTo(ox, oy));
            p.getElements().add(new LineTo(ex, ey));
            head(p, ex, ey, fx, fy);
        } else {
            double kx = ox + fx * run, ky = oy + fy * run; // where it bends
            double ex = kx + cx * turn, ey = ky + cy * turn;
            p.getElements().add(new MoveTo(ox, oy));
            p.getElements().add(new LineTo(ox + fx * (run - 18), oy + fy * (run - 18)));
            p.getElements().add(new QuadCurveTo(kx, ky, kx + cx * 18, ky + cy * 18));
            p.getElements().add(new LineTo(ex, ey));
            head(p, ex, ey, cx, cy);
        }
        add(p);
    }

    /** Adds the arrow head at (tx, ty) pointing in direction (dx, dy). */
    private void head(Path p, double tx, double ty, double dx, double dy) {
        double s = 13;
        double bx = tx - dx * s, by = ty - dy * s;
        double px = -dy, py = dx;
        p.getElements().add(new MoveTo(bx + px * s, by + py * s));
        p.getElements().add(new LineTo(tx, ty));
        p.getElements().add(new LineTo(bx - px * s, by - py * s));
    }
}
