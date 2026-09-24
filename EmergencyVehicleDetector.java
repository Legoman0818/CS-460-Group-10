import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Emergency Vehicle Detector device (the roadside antenna).
 *
 * Cross Walk tells this class which approach currently has priority when it
 * starts routing an emergency vehicle; an EMERGENCY query reports true only
 * for that direction, the way a real RF or strobe detector only "sees" the
 * vehicle currently in front of it rather than every direction at once.
 */
public class EmergencyVehicleDetector {

    private static final Color PAINT = Color.web("#f0f0f0");
    private static final Color RED   = Color.web("#cf1d1d");

    private Multiplexor.Direction activeApproach;

    /** Draws the antenna symbol at its fixed position on the intersection. */
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

    /** Records which approach an emergency vehicle is currently using. */
    public void setActiveApproach(Multiplexor.Direction approach) {
        activeApproach = approach;
    }

    /** Called once the emergency vehicle has finished its route. */
    public void clearActiveApproach() {
        activeApproach = null;
    }

    /** True only for the direction currently carrying an emergency vehicle. */
    public boolean detect(Multiplexor.Direction direction) {
        return direction == activeApproach;
    }
}
