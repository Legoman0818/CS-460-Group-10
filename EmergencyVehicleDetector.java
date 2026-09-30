import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Emergency Vehicle Detector device (the antenna). It only detects the
 * direction an emergency vehicle is currently coming from.
 */
public class EmergencyVehicleDetector {

    private static final Color PAINT = Color.web("#f0f0f0");
    private static final Color RED   = Color.web("#cf1d1d");

    private Multiplexor.Direction activeApproach; // null when there is no vehicle

    /** Draws the antenna on the intersection. */
    public EmergencyVehicleDetector(Pane root) {
        Line pole = new Line(823, 178, 823, 197);
        pole.setStroke(PAINT);
        pole.setStrokeWidth(2);
        pole.setStrokeLineCap(StrokeLineCap.ROUND);

        Text label = new Text(834, 201, "Antenna");
        label.setFill(PAINT);
        label.setFont(Font.font(12));

        Circle knob = new Circle(823, 172, 6);
        knob.setFill(PAINT);
        knob.setStroke(RED);
        knob.setStrokeWidth(1.5);

        root.getChildren().addAll(pole, label, knob);
    }

    /** Called when an emergency vehicle starts its route. */
    public void setActiveApproach(Multiplexor.Direction approach) {
        activeApproach = approach;
    }

    /** Called when the emergency vehicle has left. */
    public void clearActiveApproach() {
        activeApproach = null;
    }

    /** True if an emergency vehicle is coming from this direction. */
    public boolean detect(Multiplexor.Direction direction) {
        return direction == activeApproach;
    }
}
